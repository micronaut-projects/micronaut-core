package io.micronaut.core.io.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.core.io.service.SoftServiceLoader.ServiceCollector;

public class SoftServiceLoaderTest {

    interface TestService {
        String value();
    }

    static final class PackagePrivateConstructorService implements TestService {
        PackagePrivateConstructorService() {
        }

        @Override
        public String value() {
            return "fallback";
        }
    }

    @Test
    void findServicesUsingJrtScheme() {
        String modulename = "io.micronaut.core.test";
        ModuleFinder finder = ModuleFinder.of(Path.of("build/resources/test/test.jar"));
        ModuleLayer parent = ModuleLayer.boot();
        Configuration cf = parent.configuration().resolve(finder, ModuleFinder.of(), Set.of(modulename));
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        ModuleLayer layer = parent.defineModulesWithOneLoader(cf, scl);

        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(layer.findLoader(modulename));

            ServiceCollector<String> collector = SoftServiceLoader.newCollector("io.micronaut.inject.BeanDefinitionReference", null, layer.findLoader(modulename), Function.identity());
            List<String> services = new ArrayList<>();
            collector.collect(services::add);

            assertEquals(1, services.size());
            assertEquals("io.micronaut.logging.$PropertiesLoggingLevelsConfigurer$Definition", services.get(0));
        }
        finally {
            Thread.currentThread().setContextClassLoader(oldLoader);
        }
    }

    @Test
    void staticDefinitionFallsBackToReflectionWhenMethodHandleCannotAccessConstructor() {
        SoftServiceLoader.StaticDefinition<PackagePrivateConstructorService> serviceDefinition =
            SoftServiceLoader.StaticDefinition.of(PackagePrivateConstructorService.class.getName(), PackagePrivateConstructorService.class);

        assertEquals("fallback", serviceDefinition.load().value());
    }

    @Test
    void imageSingletonsAreNotLookedUpOutsideImageCode(@TempDir Path tempDir) throws IOException {
        assumeTrue(System.getProperty(NativeImageUtils.PROPERTY_IMAGE_CODE_KEY) == null);
        ClassLoader classLoader = getClass().getClassLoader();
        Error probe;
        List<String> errors;
        try (Recording recording = startRecordingErrors()) {
            assertNull(ServiceScanner.findStaticServiceDefinitions());
            // a forked scan looks the table up on the calling thread before it forks, and an unforked one does
            // everything on the calling thread
            SoftServiceLoader.load(TestService.class, classLoader).collectAll();
            SoftServiceLoader.load(TestService.class, classLoader).disableFork().collectAll();
            MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, TestService.class.getName());
            // JFR records an error when it is constructed: this one tells whether the errors of this thread
            // are recorded
            probe = new Error("probe " + UUID.randomUUID());
            errors = errorsOfCurrentThread(recording, tempDir.resolve("errors.jfr"));
        }

        assumeTrue(errors.contains(probe.toString()), "JFR did not record the errors of this thread");
        // without the GraalVM SDK on the class path, each lookup of the ImageSingletons class throws a NoClassDefFoundError
        assertEquals(List.of(), errors.stream().filter(error -> error.contains("ImageSingletons")).toList());
    }

    /**
     * Starts recording the errors thrown in this JVM, or aborts the test when JFR cannot be used in it.
     */
    private static Recording startRecordingErrors() {
        assumeTrue(FlightRecorder.isAvailable(), "JFR is not available in this JVM");
        Recording recording = null;
        try {
            recording = new Recording();
            recording.enable("jdk.JavaErrorThrow");
            recording.start();
            return recording;
        } catch (RuntimeException e) {
            // for example when the JFR repository cannot be created
            if (recording != null) {
                recording.close();
            }
            return abort("JFR cannot record in this JVM: " + e);
        }
    }

    /**
     * Returns the errors that the calling thread threw during the recording. JFR records the errors of every
     * thread of the JVM, so the ones thrown by code that runs next to the test are left out.
     */
    private static List<String> errorsOfCurrentThread(Recording recording, Path file) {
        long threadId = Thread.currentThread().threadId();
        List<RecordedEvent> events;
        try {
            recording.stop();
            recording.dump(file);
            events = RecordingFile.readAllEvents(file);
        } catch (IOException e) {
            return abort("JFR cannot write the recording in this JVM: " + e);
        }
        return events.stream()
            .filter(event -> event.getThread() != null && event.getThread().getJavaThreadId() == threadId)
            .map(SoftServiceLoaderTest::errorMessage)
            .toList();
    }

    private static String errorMessage(RecordedEvent event) {
        String message = event.getString("message");
        return event.getClass("thrownClass").getName() + ": " + (message == null ? "" : message);
    }

    @Test
    void imageSingletonsLookupCanBeDisabled() {
        String previous = System.getProperty("micronaut.graalvm.imagesingletons.enabled");
        try {
            System.setProperty("micronaut.graalvm.imagesingletons.enabled", "false");
            assertNull(ServiceScanner.findStaticServiceDefinitions());
            assertFalse(NativeImageUtils.hasImageSingletons());
        } finally {
            if (previous == null) {
                System.clearProperty("micronaut.graalvm.imagesingletons.enabled");
            } else {
                System.setProperty("micronaut.graalvm.imagesingletons.enabled", previous);
            }
        }
    }
}
