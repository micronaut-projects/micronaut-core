package io.micronaut.dev;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The launcher logs, once per launch, that the application runs with development settings and is not for production.
 */
class DevModeNoticeTest {

    @TempDir
    Path project;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void setUp() {
        logged.start();
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(logged);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logged);
    }

    @Test
    void theNoticeIsLoggedOnceAtTheFirstStartAndNotOnAReload() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "DevModeNoticeTest", "micronaut.server.port", "-1"))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Path greeter = src.resolve("Greeter.java");
        Files.writeString(greeter, greeter("one"));
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            List<ILoggingEvent> notices = notices();
            assertEquals(1, notices.size());
            ILoggingEvent notice = notices.get(0);
            assertEquals(Level.INFO, notice.getLevel());
            assertEquals(DevRuntime.class.getName(), notice.getLoggerName());
            String text = notice.getFormattedMessage();
            assertTrue(text.contains("'dev' environment is active, with micronaut.dev.enabled=true"), text);
            assertTrue(text.contains("strategy: restart"), text);
            // the application has an HTTP server, whose requests a failed compilation answers
            assertTrue(text.contains("answered with the compiler's"), text);
            assertTrue(text.contains("in production"), text);

            // a reload starts generation two without logging the note again
            Files.writeString(greeter, greeter("two"));
            runtime.reload();
            runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertTrue(indexOf("Reloaded: generation 2") >= 0);
            assertEquals(1, notices().size());
        } finally {
            runtime.close();
        }
    }

    @Test
    void theNoticeListsOnlyWhatTheLaunchHas() {
        String text = DevModeNotice.text(ReloadStrategy.RELOAD, OptionalInt.empty(), false, false);
        assertTrue(text.contains(DevModeNotice.HEADLINE), text);
        assertTrue(text.contains("strategy: reload"), text);
        assertFalse(text.contains("compiler's"), text);
        assertFalse(text.contains("LiveReload"), text);
        String withServer = DevModeNotice.text(ReloadStrategy.RESTART, OptionalInt.of(35729), true, true);
        assertTrue(withServer.contains("a LiveReload server listens on port 35729, and HTML pages load its script"), withServer);
        // micronaut.dev.livereload.inject-script=false: the server runs, the pages are left alone
        String noScript = DevModeNotice.text(ReloadStrategy.RESTART, OptionalInt.of(35729), true, false);
        assertTrue(noScript.contains("a LiveReload server listens on port 35729" + System.lineSeparator()), noScript);
        assertFalse(noScript.contains("load its script"), noScript);
    }

    private List<ILoggingEvent> notices() {
        return logged.list.stream().filter(e -> e.getFormattedMessage().contains(DevModeNotice.HEADLINE)).toList();
    }

    private int indexOf(String message) {
        for (int i = 0; i < logged.list.size(); i++) {
            if (logged.list.get(i).getFormattedMessage().contains(message)) {
                return i;
            }
        }
        return -1;
    }

    private static String greeter(String greeting) {
        return "package app; @jakarta.inject.Singleton public class Greeter { public String greet() { return \"" + greeting + "\"; } }";
    }
}
