package gr.novotrade.novocore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for NovoCore.
 *
 * <p>This class sits in the root package {@code gr.novotrade.novocore} deliberately, even
 * though it is built by the {@code app} module. Spring Boot's component scanning, entity
 * scanning and repository scanning are all anchored to the annotated class's own package, so
 * placing it at the root means {@code core} — and later {@code adapters} and
 * {@code modules} — are picked up without maintaining an explicit list of packages that
 * someone will eventually forget to update.
 *
 * <p>No business logic belongs in the {@code app} module. It is the Spring Boot bootstrap
 * and cross-cutting configuration only.
 */
@SpringBootApplication
public class NovoCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovoCoreApplication.class, args);
    }
}
