package gr.novotrade.novocore.core.backup;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Talks to the Google Drive API over plain HTTP.
 *
 * <h2>Why not the Google client library</h2>
 *
 * <p>{@code google-api-services-drive} pulls a large transitive tree (its own HTTP client, its own
 * JSON layer, Guava) to wrap four calls: refresh a token, look up a folder, upload a file, delete
 * one. What it costs in exchange is testability — the failure paths that matter here are HTTP ones
 * (an expired refresh token, a folder that has been deleted, a 403 for quota) and reproducing them
 * through that library means mocking the library rather than the protocol.
 *
 * <p>Against {@link HttpClient} the base URLs are a property, so the tests point this at a stub
 * server on localhost and exercise upload, delete, token refresh and every one of those failures
 * for real, with no credentials and no network. That is the same reasoning ADR 0002 used to take
 * ArchUnit as a plain library rather than through its JUnit integration.
 *
 * <h2>Resumable upload, single request</h2>
 *
 * <p>Uploads start a resumable session and then send the file in one {@code PUT}. Simple uploads
 * cap out well below the size of a real database dump; the resumable endpoint has no such limit.
 * Chunked resume-after-interruption is deliberately not built yet — it would matter for a
 * multi-gigabyte artefact on a poor connection, and until there is one, a failed upload is retried
 * whole by the next night's run, which is recorded and visible.
 */
@Component
public class GoogleDriveClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveClient.class);

    private static final String ARTEFACT_MIME_TYPE = "application/octet-stream";

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String tokenEndpoint;
    private final String driveApiBase;
    private final String driveUploadBase;

    GoogleDriveClient(
            @Value("${novocore.backup.drive.token-endpoint:https://oauth2.googleapis.com/token}")
            String tokenEndpoint,
            @Value("${novocore.backup.drive.api-base:https://www.googleapis.com/drive/v3}")
            String driveApiBase,
            @Value("${novocore.backup.drive.upload-base:https://www.googleapis.com/upload/drive/v3}")
            String driveUploadBase) {
        this.tokenEndpoint = tokenEndpoint;
        this.driveApiBase = driveApiBase;
        this.driveUploadBase = driveUploadBase;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                // No automatic redirect following. Drive's resumable session URI is handed over in
                // a Location header and must be used as given; a client that transparently
                // followed redirects could send the artefact somewhere the response did not
                // intend.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Exchanges the destination's refresh token for a short-lived access token.
     *
     * <p>Not cached. A backup runs nightly and a token lasts an hour, so a cache would hold a
     * value that is always expired by the time it is next wanted, while adding a way for two
     * destinations to share one another's credentials by mistake.
     */
    String accessToken(DriveDestination destination) {
        String body = form(Map.of(
                "client_id", destination.clientId(),
                "client_secret", destination.clientSecret(),
                "refresh_token", destination.refreshToken(),
                "grant_type", "refresh_token"));

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), "refresh the access token for " + destination.label());

        if (response.statusCode() != 200) {
            // Google reports a dead refresh token as 400 invalid_grant, which is the single most
            // likely thing to be wrong here and reads as nothing in particular unless it is named.
            throw new DriveException(
                    ("Google refused the refresh token for %s (HTTP %d): %s. A refresh token stops "
                            + "working when the account's password changes, when consent is "
                            + "revoked, or after six months without use — the fix is to run the "
                            + "consent flow for that account again.")
                            .formatted(destination.label(), response.statusCode(),
                                    firstLine(response.body())));
        }

        JsonNode parsed = json.readTree(response.body());
        JsonNode token = parsed.get("access_token");
        if (token == null || token.asString().isBlank()) {
            throw new DriveException(
                    "Google's token response for %s contained no access_token."
                            .formatted(destination.label()));
        }
        return token.asString();
    }

    /**
     * Confirms the target folder exists and is a folder, without uploading anything.
     *
     * @return the folder's name, for the configuration report
     */
    String verifyFolder(DriveDestination destination, String accessToken) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(
                        "%s/files/%s?fields=id,name,mimeType,trashed&supportsAllDrives=true"
                                .formatted(driveApiBase, encode(destination.folderId()))))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build(), "look up the backup folder for " + destination.label());

        if (response.statusCode() == 404) {
            throw new DriveException(
                    ("The folder id configured for %s (%s) does not exist, or this account cannot "
                            + "see it. Check backup.drive.%s.folder-id — it is the last path "
                            + "segment of the folder's URL, and it must belong to the same account "
                            + "the refresh token was issued for.")
                            .formatted(destination.label(), destination.folderId(),
                                    destination.key()));
        }
        if (response.statusCode() != 200) {
            throw new DriveException("Drive rejected the folder lookup for %s (HTTP %d): %s"
                    .formatted(destination.label(), response.statusCode(),
                            firstLine(response.body())));
        }

        JsonNode folder = json.readTree(response.body());
        if (folder.path("trashed").asBoolean(false)) {
            // A trashed folder still resolves and still accepts uploads, which would put every
            // backup somewhere that empties itself after thirty days.
            throw new DriveException(
                    "The backup folder for %s is in the Drive trash. Files uploaded to it would be "
                            .formatted(destination.label()) + "deleted automatically.");
        }
        String mimeType = folder.path("mimeType").asString("");
        if (!"application/vnd.google-apps.folder".equals(mimeType)) {
            throw new DriveException(
                    "The id configured for %s points at a %s, not a folder."
                            .formatted(destination.label(), mimeType.isBlank() ? "file" : mimeType));
        }
        return folder.path("name").asString(destination.folderId());
    }

    /**
     * Uploads one artefact into the destination's folder.
     *
     * @return Drive's file id, which is what makes the copy deletable later by retention
     */
    String upload(DriveDestination destination, String accessToken, Path artefact,
            String artefactName) {
        long size;
        try {
            size = Files.size(artefact);
        } catch (IOException e) {
            throw new DriveException("Cannot read %s to upload it: %s"
                    .formatted(artefact, e.getMessage()), e);
        }

        String metadata = json.writeValueAsString(Map.of(
                "name", artefactName,
                "parents", List.of(destination.folderId())));

        HttpResponse<String> start = send(HttpRequest.newBuilder(URI.create(
                        driveUploadBase + "/files?uploadType=resumable&supportsAllDrives=true"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type", ARTEFACT_MIME_TYPE)
                .header("X-Upload-Content-Length", String.valueOf(size))
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(metadata, StandardCharsets.UTF_8))
                .build(), "start an upload to " + destination.label());

        if (start.statusCode() != 200) {
            throw new DriveException("Drive would not start an upload to %s (HTTP %d): %s"
                    .formatted(destination.label(), start.statusCode(), firstLine(start.body())));
        }
        String sessionUri = start.headers().firstValue("Location")
                .orElseThrow(() -> new DriveException(
                        "Drive accepted the upload request for %s but returned no session URI."
                                .formatted(destination.label())));

        // Streamed from the file rather than read into memory. A database dump is the largest
        // thing this application handles, and buffering it would make the backup the reason the
        // process runs out of heap.
        HttpRequest.BodyPublisher body;
        try {
            body = HttpRequest.BodyPublishers.ofFile(artefact);
        } catch (IOException e) {
            throw new DriveException("Cannot open %s to upload it: %s"
                    .formatted(artefact, e.getMessage()), e);
        }

        HttpResponse<String> finished = send(HttpRequest.newBuilder(URI.create(sessionUri))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", ARTEFACT_MIME_TYPE)
                .timeout(Duration.ofHours(6))
                .PUT(body)
                .build(), "upload the backup to " + destination.label());

        if (finished.statusCode() != 200 && finished.statusCode() != 201) {
            throw new DriveException("Drive rejected the upload to %s (HTTP %d): %s"
                    .formatted(destination.label(), finished.statusCode(),
                            firstLine(finished.body())));
        }

        JsonNode file = json.readTree(finished.body());
        String fileId = file.path("id").asString("");
        if (fileId.isBlank()) {
            throw new DriveException(
                    ("The upload to %s completed but Drive returned no file id, so this copy "
                            + "cannot be managed or deleted later.").formatted(destination.label()));
        }
        log.info("Uploaded {} ({} bytes) to {}.", artefactName, size, destination.label());
        return fileId;
    }

    /**
     * Deletes one artefact from a destination, for retention.
     *
     * <p>A 404 is treated as success: the goal is that the file is not there, and somebody having
     * deleted it by hand is not a failure of that goal. Anything else is reported, because a
     * retention pass that silently fails to delete is how a Drive quietly fills up.
     */
    void delete(DriveDestination destination, String accessToken, String fileId) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(
                        "%s/files/%s?supportsAllDrives=true"
                                .formatted(driveApiBase, encode(fileId))))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(Duration.ofSeconds(120))
                .DELETE()
                .build(), "delete an expired backup from " + destination.label());

        int status = response.statusCode();
        if (status != 204 && status != 200 && status != 404) {
            throw new DriveException("Drive refused to delete %s from %s (HTTP %d): %s"
                    .formatted(fileId, destination.label(), status, firstLine(response.body())));
        }
    }

    private HttpResponse<String> send(HttpRequest request, String what) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DriveException("Could not %s: %s".formatted(what, e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DriveException("Interrupted trying to " + what, e);
        }
    }

    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * The first line of an error body, capped.
     *
     * <p>Google's errors are JSON several hundred characters long whose useful part is the
     * {@code message} field; when parsing fails, the raw head is still more informative than a
     * status code alone. Never logs the request, which carried the client secret.
     */
    private static String firstLine(String body) {
        if (body == null || body.isBlank()) {
            return "(no body)";
        }
        try {
            JsonNode parsed = new ObjectMapper().readTree(body);
            String message = parsed.path("error").path("message").asString("");
            if (!message.isBlank()) {
                return message;
            }
            String description = parsed.path("error_description").asString("");
            if (!description.isBlank()) {
                return description;
            }
        } catch (RuntimeException e) {
            // Not JSON. Fall through to the raw head.
        }
        String head = body.strip().split("\\R")[0];
        return head.length() <= 300 ? head : head.substring(0, 300) + "…";
    }

    /** Anything that went wrong talking to Drive, with a message written for a human. */
    static class DriveException extends RuntimeException {

        DriveException(String message) {
            super(message);
        }

        DriveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
