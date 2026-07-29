package gr.novotrade.novocore.core.backup;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for Google Drive, speaking the parts of the real protocol NovoCore uses.
 *
 * <p>This is why {@link GoogleDriveClient} was written against {@code HttpClient} rather than the
 * Google client library: the failures that matter here are protocol-level — an expired refresh
 * token, a folder that has been deleted, a rejected upload — and with the base URLs as properties
 * they can all be produced for real, over a socket, with no credentials and no network. Mocking
 * the Google library instead would only prove that the mock was configured the way the test
 * expected.
 *
 * <p>Uploads are verified rather than merely counted: the bytes received are kept, so a test can
 * assert that what arrived at the destination is byte-for-byte the artefact on disk. An upload
 * that silently sent the wrong file, or an empty one, would otherwise look identical to a correct
 * one from the outside.
 */
final class StubDriveServer implements AutoCloseable {

    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";

    private final HttpServer server;
    private final Map<String, byte[]> uploaded = new LinkedHashMap<>();
    private final List<String> deleted = new ArrayList<>();
    private final AtomicInteger nextFileId = new AtomicInteger(1);

    /** Set to make the token endpoint reject the refresh token, as Google does when it expires. */
    volatile boolean refreshTokenRejected;

    /** Set to make the configured folder look deleted. */
    volatile boolean folderMissing;

    /** Set to make uploads fail after the session starts, as a quota error does. */
    volatile boolean uploadRejected;

    StubDriveServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", this::handleToken);
        server.createContext("/drive/v3/files", this::handleFiles);
        server.createContext("/upload/drive/v3/files", this::handleUploadStart);
        server.createContext("/upload-session/", this::handleUploadBody);
        server.setExecutor(null);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    String tokenEndpoint() {
        return baseUrl() + "/token";
    }

    String apiBase() {
        return baseUrl() + "/drive/v3";
    }

    String uploadBase() {
        return baseUrl() + "/upload/drive/v3";
    }

    Map<String, byte[]> uploaded() {
        return uploaded;
    }

    List<String> deleted() {
        return deleted;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // -------------------------------------------------------------------------------------

    private void handleToken(HttpExchange exchange) throws IOException {
        drain(exchange.getRequestBody());
        if (refreshTokenRejected) {
            // Google's actual shape for a dead refresh token, which is the single most likely
            // thing to be wrong with a destination that worked yesterday.
            respond(exchange, 400,
                    "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired "
                            + "or revoked.\"}");
            return;
        }
        respond(exchange, 200,
                "{\"access_token\":\"stub-access-token\",\"expires_in\":3599,"
                        + "\"token_type\":\"Bearer\"}");
    }

    /** Folder lookup and file deletion both live under /drive/v3/files. */
    private void handleFiles(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String id = path.substring(path.lastIndexOf('/') + 1);

        if ("DELETE".equals(exchange.getRequestMethod())) {
            deleted.add(id);
            uploaded.values().removeIf(bytes -> false);
            uploaded.entrySet().removeIf(entry -> entry.getKey().equals(nameOfFileId(id)));
            respond(exchange, 204, "");
            return;
        }

        if (folderMissing) {
            respond(exchange, 404,
                    "{\"error\":{\"code\":404,\"message\":\"File not found: " + id + "\"}}");
            return;
        }
        respond(exchange, 200,
                "{\"id\":\"" + id + "\",\"name\":\"NovoCore Backups\",\"mimeType\":\""
                        + FOLDER_MIME + "\",\"trashed\":false}");
    }

    private void handleUploadStart(HttpExchange exchange) throws IOException {
        String metadata = new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
        String name = between(metadata, "\"name\":\"", "\"");
        String sessionId = "session-" + nextFileId.getAndIncrement();
        pendingNames.put(sessionId, name);

        exchange.getResponseHeaders().add("Location", baseUrl() + "/upload-session/" + sessionId);
        respond(exchange, 200, "{}");
    }

    private void handleUploadBody(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String sessionId = path.substring(path.lastIndexOf('/') + 1);
        byte[] body = exchange.getRequestBody().readAllBytes();

        if (uploadRejected) {
            respond(exchange, 403,
                    "{\"error\":{\"code\":403,\"message\":\"The user's Drive storage quota has "
                            + "been exceeded.\"}}");
            return;
        }

        String name = pendingNames.getOrDefault(sessionId, sessionId);
        uploaded.put(name, body);
        String fileId = "file-" + sessionId;
        fileIdToName.put(fileId, name);
        respond(exchange, 200, "{\"id\":\"" + fileId + "\",\"name\":\"" + name + "\"}");
    }

    private final Map<String, String> pendingNames = new LinkedHashMap<>();
    private final Map<String, String> fileIdToName = new LinkedHashMap<>();

    private String nameOfFileId(String fileId) {
        return fileIdToName.getOrDefault(fileId, "");
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        if (from < 0) {
            return "";
        }
        int begin = from + start.length();
        int to = source.indexOf(end, begin);
        return to < 0 ? source.substring(begin) : source.substring(begin, to);
    }

    private static void drain(InputStream stream) throws IOException {
        try (stream) {
            stream.readAllBytes();
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }
}
