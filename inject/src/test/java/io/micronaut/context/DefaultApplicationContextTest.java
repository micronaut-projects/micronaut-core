package io.micronaut.context;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import spock.lang.Issue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultApplicationContextTest {
    @Issue("https://github.com/micronaut-projects/micronaut-test/issues/615#issuecomment-1516355815")
    @Test
    public void applicationContextShouldShutDownTheEnvironmentItCreated() {
        ApplicationContext ctx = ApplicationContext.builder().build();
        ctx.start();
        Environment env = ctx.getEnvironment();
        assertTrue(env.isRunning());
        ctx.stop();
        assertFalse(env.isRunning(), "expected to be stopped");
        assertFalse(ctx.isRunning(), "expected to be stopped");
    }

    @Test
    public void applicationContextShouldNotStopTheEnvironmentItDidNotCreate() {
        ApplicationContext ctx = ApplicationContext.builder().build();
        ctx.start();

        // providing ctx with an external environment
        ApplicationContext ctx2 = ApplicationContext.create(ctx.getEnvironment());
        Assertions.assertEquals(ctx.getEnvironment(), ctx2.getEnvironment());
        ctx2.start();
        ctx2.stop();

        assertTrue(ctx.getEnvironment().isRunning(), "shouldn't stop an external environment");

        ctx.stop();
        assertFalse(ctx.isRunning(), "expected to be stopped");
        assertFalse(ctx.getEnvironment().isRunning());
    }

    @Test
    public void applicationContextShouldRejectNullConstructorArguments() {
        ClassPathResourceLoader resourceLoader = ClassPathResourceLoader.defaultLoader(getClass().getClassLoader());

        Assertions.assertAll(
            () -> assertRejectsNullArgument(
                "resourceLoader",
                () -> new DefaultApplicationContext((ClassPathResourceLoader) null, "custom-loader")
            ),
            () -> assertRejectsNullArgument(
                "environmentNames",
                () -> new DefaultApplicationContext(resourceLoader, (String[]) null)
            )
        );
    }

    @Test
    public void applicationContextShouldUseDefaultAndProvidedResourceLoaders(@TempDir Path resourceRoot) throws IOException {
        Path resourceDirectory = Files.createDirectories(resourceRoot.resolve("context-loader"));
        Files.writeString(resourceDirectory.resolve("message.txt"), "provided-loader", StandardCharsets.UTF_8);

        try (ApplicationContext ctx = new DefaultApplicationContext("default-loader").start()) {
            Assertions.assertSame(DefaultApplicationContext.class.getClassLoader(), ctx.getClassLoader());
            Assertions.assertSame(DefaultApplicationContext.class.getClassLoader(), ctx.getEnvironment().getClassLoader());
            assertTrue(ctx.getEnvironment().getActiveNames().contains("default-loader"));
        }
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{resourceRoot.toUri().toURL()}, getClass().getClassLoader())) {
            ClassPathResourceLoader resourceLoader = ClassPathResourceLoader.defaultLoader(classLoader);
            try (ApplicationContext ctx = new DefaultApplicationContext(resourceLoader, "custom-loader").start()) {
                Assertions.assertSame(classLoader, ctx.getClassLoader());
                Assertions.assertSame(classLoader, ctx.getEnvironment().getClassLoader());
                assertTrue(ctx.getEnvironment().getActiveNames().contains("custom-loader"));

                Optional<InputStream> resource = ctx.getEnvironment()
                    .getResourceAsStream("classpath:context-loader/message.txt");
                assertTrue(resource.isPresent());
                try (InputStream inputStream = resource.get()) {
                    Assertions.assertEquals(
                        "provided-loader",
                        new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    );
                }
            }
        }
    }

    @Test
    public void applicationContextShouldDelegateEnvironmentOperations() {
        try (DefaultApplicationContext ctx = new DefaultApplicationContext("delegate-env")) {
            Assertions.assertSame(ctx, ctx.start());
            Assertions.assertSame(ctx.getEnvironment(), ctx.delegate());
            ctx.getEnvironment().addPropertySource(PropertySource.of(
                "delegate-test",
                Map.of(
                    "delegate.message", "hello",
                    "delegate.nested.value", "present"
                )
            ));

            Assertions.assertSame(ctx.getEnvironment().getConversionService(), ctx.getConversionService());
            assertTrue(ctx.containsProperty("delegate.message"));
            assertTrue(ctx.containsProperties("delegate.nested"));
            Assertions.assertEquals(Optional.of("hello"), ctx.resolvePlaceholders("${delegate.message}"));
            Assertions.assertEquals("hello", ctx.resolveRequiredPlaceholders("${delegate.message}"));

            RegisteredSingleton singleton = new RegisteredSingleton();
            Assertions.assertSame(ctx, ctx.registerSingleton(RegisteredSingleton.class, singleton, null, false));
            Assertions.assertSame(singleton, ctx.getBean(RegisteredSingleton.class));
        }
    }

    private static void assertRejectsNullArgument(String argumentName, Executable executable) {
        NullPointerException exception = Assertions.assertThrows(NullPointerException.class, executable);
        Assertions.assertEquals("Argument [" + argumentName + "] cannot be null", exception.getMessage());
    }

    private static final class RegisteredSingleton {
    }
}
