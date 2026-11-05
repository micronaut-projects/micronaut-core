/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.python;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GraalPyReusableBootstrapConfigurationTest {
    @TempDir
    Path configurationDirectory;

    @AfterEach
    @BeforeEach
    void resetRuntime() {
        PythonContextRuntime.setReuseContext(false);
        PythonContextRuntime.resetContext();
    }

    @Test
    void legacyBootstrapHonorsConfiguredHostClassLookup() throws IOException {
        Files.writeString(configurationDirectory.resolve("application.properties"),
            "graalpy.context.host-class-lookup[0]=example.allowed\n");
        try (URLClassLoader loader = configurationClassLoader();
             Context context = GraalPyContextFactory.bootstrapReusableContext(loader, Map.of(), GraalPyContextFactory.APPLICATION_MAIN)) {
            assertThrows(PolyglotException.class, () -> context.eval("python",
                "import java; java.type('org.junit.jupiter.api.Test')"));
        }
    }

    @Test
    void configurationOnlyBindingExcludesConvertersAndRegistrars() throws IOException {
        Files.writeString(configurationDirectory.resolve("application.properties"), """
            graalpy.context.host-class-lookup[0]=example.allowed
            graalpy.context.options.python.WarnExperimentalFeatures=true
            """);
        try (URLClassLoader loader = configurationClassLoader();
             ApplicationContext application = ApplicationContext.builder(loader)
                 .overrideConfigLocations(configurationDirectory.toUri().toString())
                 .eagerBeansEnabled(false)
                 .eventsEnabled(false)
                 .beansPredicate(bean -> bean.getBeanType() == GraalPyContextConfiguration.class)
                 .properties(Map.of("test.bootstrap.converters", true))
                 .start()) {
            GraalPyContextConfiguration configuration = application.getBean(GraalPyContextConfiguration.class);
            assertEquals(List.of("example.allowed"), configuration.getHostClassLookup());
            assertEquals("true", configuration.getOptions().get("python.WarnExperimentalFeatures"));
            assertFalse(application.containsBean(GraalPyContextFactory.class));
            assertFalse(PythonContextRuntime.isInitialized());
        }
    }

    @Test
    void legacyExplicitOptionsOverrideApplicationOptions() throws IOException {
        Files.writeString(configurationDirectory.resolve("application.properties"),
            "graalpy.context.options.log.level=INVALID\n");
        try (URLClassLoader loader = configurationClassLoader()) {
            GraalPyContextConfiguration configuration;
            try (ApplicationContext application = configurationContext(loader)) {
                configuration = application.getBean(GraalPyContextConfiguration.class);
                assertEquals("INVALID", configuration.getOptions().get("log.level"));
            }
            configuration.getBuilder().options(Map.of("log.level", "WARNING"));
            try (Context context = GraalPyContextFactory.bootstrapReusableContext(loader, configuration, GraalPyContextFactory.APPLICATION_MAIN)) {
                assertEquals(2, context.eval("python", "1 + 1").asInt());
            }
            resetRuntime();
            try (Context context = GraalPyContextFactory.bootstrapReusableContext(loader,
                Map.of("log.level", "WARNING"), GraalPyContextFactory.APPLICATION_MAIN)) {
                assertEquals(2, context.eval("python", "1 + 1").asInt());
            }
        }
    }

    @Test
    void reusedBootstrapDoesNotReadConfigurationAgain() throws IOException {
        try (Context context = GraalPyContextFactory.bootstrapReusableContext(getClass().getClassLoader())) {
            ClassLoader unreadableConfiguration = new ClassLoader(getClass().getClassLoader()) {
                @Override
                public Enumeration<URL> getResources(String name) {
                    throw new AssertionError("a reused context read classpath configuration: " + name);
                }
            };
            assertSame(context, GraalPyContextFactory.bootstrapReusableContext(unreadableConfiguration,
                Map.of("log.level", "INVALID"), GraalPyContextFactory.APPLICATION_MAIN));
            assertEquals(2, context.eval("python", "1 + 1").asInt());
        }
    }

    @Test
    void configurationOnlyContextPreservesExistingBootstrapRecord() throws IOException {
        try (Context expected = Context.newBuilder("python").build();
             ApplicationContext original = ApplicationContext.builder()
                 .eagerBeansEnabled(false)
                 .beansPredicate(bean -> bean.getBeanType() == PythonRuntimeBootstrapShutdownListener.class
                     || ApplicationEventPublisher.class.isAssignableFrom(bean.getBeanType()))
                 .beanDefinitions(RuntimeBeanDefinition.builder(Context.class, () -> {
                     PythonContextRuntime.setContext(expected, getClass().getClassLoader());
                     return expected;
                 }).qualifier(Qualifiers.byName("python")).singleton(true).build())
                 .start();
             URLClassLoader loader = configurationClassLoader()) {
            try (ApplicationContext temporary = configurationContext(loader)) {
                temporary.getBean(GraalPyContextConfiguration.class);
                assertFalse(temporary.containsBean(GraalPyContextFactory.class));
                assertFalse(PythonContextRuntime.isInitialized());
            }
            assertSame(expected, PythonContextRuntime.getContext());
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void legacyBootstrapPropagatesNativeAccessOptionsAndPhysicalResources() throws IOException {
        Path resources = prepareNativeConfiguration(true);
        try (URLClassLoader loader = configurationClassLoader();
             Context context = GraalPyContextFactory.bootstrapReusableContext(loader, Map.of(), GraalPyContextFactory.APPLICATION_MAIN)) {
            assertTrue(context.eval("python", "import socket; hasattr(socket, 'AF_UNIX')").asBoolean());
            assertEquals(42, context.eval("python", "from bootstrap_configuration_probe import VALUE; VALUE").asInt());
        }
        assertTrue(Files.isRegularFile(resources.resolve("src/bootstrap_configuration_probe.py")));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void legacyNativeBootstrapDoesNotGrantNativeAccessByDefault() throws IOException {
        prepareNativeConfiguration(false);
        try (URLClassLoader loader = configurationClassLoader()) {
            PolyglotException failure = assertThrows(PolyglotException.class, () ->
                GraalPyContextFactory.bootstrapReusableContext(loader, Map.of(), GraalPyContextFactory.APPLICATION_MAIN));
            assertTrue(failure.getMessage().toLowerCase(java.util.Locale.ROOT).contains("native access"), failure.getMessage());
            assertFalse(PythonContextRuntime.isInitialized());
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void legacyExplicitOptionsOverrideConfiguredNativeBackend() throws IOException {
        prepareNativeConfiguration(true);
        try (URLClassLoader loader = configurationClassLoader();
             Context context = GraalPyContextFactory.bootstrapReusableContext(loader,
                 Map.of("python.PosixModuleBackend", "java"), GraalPyContextFactory.APPLICATION_MAIN)) {
            assertFalse(context.eval("python", "import socket; hasattr(socket, 'AF_UNIX')").asBoolean());
            assertEquals(42, context.eval("python", "from bootstrap_configuration_probe import VALUE; VALUE").asInt());
        }
    }

    private Path prepareNativeConfiguration(boolean nativeAccess) throws IOException {
        Path resources = configurationDirectory.resolve("resources");
        try (VirtualFileSystem vfs = VirtualFileSystem.newBuilder()
            .resourceDirectory(GraalPyContextFactory.APPLICATION_PATH)
            .resourceClassLoader(getClass().getClassLoader())
            .build()) {
            GraalPyResources.extractVirtualFileSystemResources(vfs, resources);
        }
        Files.writeString(resources.resolve("src/bootstrap_configuration_probe.py"), "VALUE = 42\n");
        Files.writeString(configurationDirectory.resolve("application.properties"),
            "graalpy.context.resource-directory=" + resources + "\n"
                + (nativeAccess ? "graalpy.context.allow-native-access=true\n" : "")
                + "graalpy.context.options.python.PosixModuleBackend=native\n");
        return resources;
    }

    private ApplicationContext configurationContext(ClassLoader loader) {
        return ApplicationContext.builder(loader)
            .overrideConfigLocations(configurationDirectory.toUri().toString())
            .eagerBeansEnabled(false)
            .eventsEnabled(false)
            .beansPredicate(bean -> bean.getBeanType() == GraalPyContextConfiguration.class)
            .start();
    }

    private URLClassLoader configurationClassLoader() throws IOException {
        return new URLClassLoader(new URL[] {configurationDirectory.toUri().toURL()}, getClass().getClassLoader());
    }

    @Singleton
    @Requires(property = "test.bootstrap.converters", value = "true")
    static final class BootstrapConverter implements TypeConverter<String, Boolean> {
        BootstrapConverter() {
            throw new AssertionError("configuration binding instantiated a converter");
        }

        @Override
        public Optional<Boolean> convert(String object, Class<Boolean> targetType, ConversionContext context) {
            return Optional.empty();
        }
    }

    @Singleton
    @Requires(property = "test.bootstrap.converters", value = "true")
    static final class BootstrapRegistrar implements TypeConverterRegistrar {
        BootstrapRegistrar() {
            throw new AssertionError("configuration binding instantiated a registrar");
        }

        @Override
        public void register(MutableConversionService conversionService) {
        }
    }
}
