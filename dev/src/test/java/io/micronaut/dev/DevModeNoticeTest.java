package io.micronaut.dev;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.dev.http.DevAccessLinks;
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
            // the link to the development pages with this launch's token: the endpoint, since there is no Control Panel;
            // the launcher binds the server to the loopback address, so no warning, and no LiveReload server runs
            int port = runtime.awaitGeneration(1, Duration.ofSeconds(1)).getBean(io.micronaut.runtime.server.EmbeddedServer.class).getPort();
            assertEquals(DevModeNotice.HEADLINE + System.lineSeparator()
                + "Development pages: http://localhost:" + port + "/dev?token=" + runtime.access().token(), text);
            assertEquals("localhost", runtime.awaitGeneration(1, Duration.ofSeconds(1)).getEnvironment().getProperty("micronaut.server.host", String.class).orElseThrow());

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
    void theNoticeIsTheHeadlineAndTheLiveReloadServerWhenItRuns() {
        assertEquals(DevModeNotice.HEADLINE, DevModeNotice.text(OptionalInt.empty(), null));
        assertEquals(DevModeNotice.HEADLINE + System.lineSeparator() + "LiveReload server listening on port 35729",
            DevModeNotice.text(OptionalInt.of(35729), null));
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

    @Test
    void theNoticeShowsOneLineWithTheTokenAndWarnsOnlyWhenOtherMachinesReachTheServer() {
        String text = DevModeNotice.text(OptionalInt.of(35729),
            new DevAccessLinks.Link("http://localhost:8080/control-panel?token=abc", "localhost:8080", false));
        assertEquals(DevModeNotice.HEADLINE + System.lineSeparator()
            + "Development pages: http://localhost:8080/control-panel?token=abc" + System.lineSeparator()
            + "LiveReload server listening on port 35729", text);

        String everywhere = DevModeNotice.text(OptionalInt.empty(),
            new DevAccessLinks.Link("http://localhost:8080/dev", "all interfaces (*:8080)", true));
        assertEquals(DevModeNotice.HEADLINE + System.lineSeparator()
            + "Development pages: http://localhost:8080/dev" + System.lineSeparator()
            + "HTTP server listening on all interfaces (*:8080), reachable from other machines: set micronaut.server.host=localhost to restrict it",
            everywhere);
    }

    @Test
    void theServerListensOnEveryInterfaceWhenTheLauncherIsToldTo() throws Exception {
        DevRuntime runtime = launch("micronaut.dev.bind-loopback=false\n", "");
        try {
            int port = runtime.awaitGeneration(1, Duration.ofSeconds(1)).getBean(io.micronaut.runtime.server.EmbeddedServer.class).getPort();
            String text = notices().get(0).getFormattedMessage();
            assertTrue(text.contains("HTTP server listening on all interfaces (*:" + port + "), reachable from other machines"), text);
            assertTrue(runtime.awaitGeneration(1, Duration.ofSeconds(1)).getEnvironment().getProperty("micronaut.server.host", String.class).isEmpty());
        } finally {
            runtime.close();
        }
    }

    @Test
    void theApplicationsOwnServerHostWinsOverTheLoopbackDefault() throws Exception {
        DevRuntime runtime = launch("", ", \"micronaut.server.host\", \"127.0.0.1\"");
        try {
            int port = runtime.awaitGeneration(1, Duration.ofSeconds(1)).getBean(io.micronaut.runtime.server.EmbeddedServer.class).getPort();
            String text = notices().get(0).getFormattedMessage();
            // a loopback address: the link names it, and no warning
            assertTrue(text.contains("Development pages: http://127.0.0.1:" + port + "/dev?token="), text);
            assertFalse(text.contains("reachable from other machines"), text);
        } finally {
            runtime.close();
        }
    }

    @Test
    void theLoopbackDefaultHasTheLowestPrecedence() {
        try (io.micronaut.context.ApplicationContext context = io.micronaut.context.ApplicationContext.builder()
            .propertySources(DevApplicationContextConfigurer.loopback())
            .start()) {
            assertEquals("localhost", context.getProperty("micronaut.server.host", String.class).orElseThrow());
        }
        // the application's configuration, a system property or an environment variable wins
        try (io.micronaut.context.ApplicationContext context = io.micronaut.context.ApplicationContext.builder()
            .propertySources(DevApplicationContextConfigurer.loopback(),
                io.micronaut.context.env.PropertySource.of("application", java.util.Map.of("micronaut.server.host", "0.0.0.0")))
            .start()) {
            assertEquals("0.0.0.0", context.getProperty("micronaut.server.host", String.class).orElseThrow());
        }
    }

    private DevRuntime launch(String manifest, String properties) throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "DevModeNoticeTest", "micronaut.server.port", "-1"%s))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """.formatted(properties));
        Files.writeString(src.resolve("Greeter.java"), greeter("one"));
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
            """ + manifest);
        return new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
    }
}
