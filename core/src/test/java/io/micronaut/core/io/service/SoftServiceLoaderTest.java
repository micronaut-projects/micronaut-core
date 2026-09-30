package io.micronaut.core.io.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

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
        // the JFR types are in JfrErrorRecorder only: with one of them in a method signature of this class, JUnit
        // could not discover the tests on a JVM without the jdk.jfr module
        assumeTrue(JfrErrorRecorder.isSupported(), "This JVM has no jdk.jfr module");
        ClassLoader classLoader = getClass().getClassLoader();
        // The table is looked up in two places: the constructor of the scan's task, and
        // findMicronautMetaServiceEntries. Only the errors of this thread are looked at.
        List<String> errors = JfrErrorRecorder.errorsThrownByCurrentThread(tempDir.resolve("errors.jfr"), () -> {
            assertNull(ServiceScanner.findStaticServiceDefinitions());
            // A forked scan creates its task, and so makes the first lookup, on this thread. The second one is made
            // by whichever thread runs the task: this one, or a worker of the common pool.
            SoftServiceLoader.load(TestService.class, classLoader).collectAll();
            // an unforked scan and the direct call make both lookups on this thread
            SoftServiceLoader.load(TestService.class, classLoader).disableFork().collectAll();
            MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, TestService.class.getName());
        });

        // without the GraalVM SDK on the class path, each lookup of the ImageSingletons class throws a NoClassDefFoundError
        assertEquals(List.of(), errors.stream().filter(error -> error.contains("ImageSingletons")).toList());
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
