package io.micronaut.core.io.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

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
