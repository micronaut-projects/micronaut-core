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
package io.micronaut.python.compiler;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.python.ValueCoercible;
import io.micronaut.python.processing.PythonAnnotationProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The main and the test Python sources of a project are compiled separately, each into its own
 * virtual file system root, and both roots are on the class path at test time.
 */
class PythonSourceRootsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void testRootImportsAndInjectsClassesOfTheMainRoot() throws Exception {
        Path mainSources = Files.createDirectories(temporaryDirectory.resolve("src/main/python/example"));
        Files.writeString(mainSources.resolve("greeting_service.py"), """
            from jakarta.inject import Singleton

            @Singleton
            class GreetingService:
                def greet(self, name: str) -> str:
                    return f"Hello {name}"
            """);
        Path testSources = Files.createDirectories(temporaryDirectory.resolve("src/test/python/example"));
        // the test root imports names of the shim packages the main root does not (Named, Prototype)
        Files.writeString(testSources.resolve("greeting_consumer.py"), """
            from jakarta.inject import Named
            from micronaut.context.annotation import Prototype
            from example.greeting_service import GreetingService

            @Prototype
            @Named("consumer")
            class GreetingConsumer:
                def __init__(self, service: GreetingService):
                    self.service = service

                def greet(self, name: str) -> str:
                    return self.service.greet(name)
            """);
        File mainOutput = Files.createDirectories(temporaryDirectory.resolve("classes/main")).toFile();
        File testOutput = Files.createDirectories(temporaryDirectory.resolve("classes/test")).toFile();

        PyronautCompiler.builder()
            .pythonSrc(mainSources.getParent().toString())
            .targetDir(mainOutput)
            .build()
            .compile();
        // the main classes (the generated bridges of its Python classes) are on the test compile class path
        PyronautCompiler.builder()
            .pythonSrc(testSources.getParent().toString())
            .targetDir(testOutput)
            .classpath(List.of(mainOutput))
            .build()
            .compile();

        assertTrue(new File(testOutput, "META-INF/" + PythonAnnotationProcessor.APPLICATION_SRC_PATH + "jakarta/inject/__init__.py").isFile());
        assertTrue(new File(mainOutput, "META-INF/" + PythonAnnotationProcessor.APPLICATION_SRC_PATH + "jakarta/inject/__init__.py").isFile());

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{mainOutput.toURI().toURL(), testOutput.toURI().toURL()})) {
            Class<?> service = classLoader.loadClass("example.GreetingService");
            Class<?> consumer = classLoader.loadClass("example.GreetingConsumer");
            // the type imported from the main root is resolved through its bridge on the class path, not to Object
            List<List<Class<?>>> constructors = Arrays.stream(consumer.getDeclaredConstructors())
                .map(constructor -> List.<Class<?>>of(constructor.getParameterTypes()))
                .toList();
            assertTrue(constructors.contains(List.of(service)), "constructor parameter types: " + constructors);

            try (ApplicationContext context = ApplicationContext.builder().classLoader(classLoader).build().start()) {
                Object bean = context.getBean(consumer);
                assertEquals("Hello Python", ((ValueCoercible) bean).asPolyglotValue().invokeMember("greet", "Python").asString());
            }
        }
    }

    @Test
    void bothRootsContributeToTheSamePythonPackage() throws Exception {
        Path mainSources = Files.createDirectories(temporaryDirectory.resolve("src/main/python/example"));
        Files.writeString(mainSources.resolve("greeting_service.py"), """
            from jakarta.inject import Singleton

            @Singleton
            class GreetingService:
                def greet(self, name: str) -> str:
                    return f"Hello {name}"
            """);
        Path testSources = Files.createDirectories(temporaryDirectory.resolve("src/test/python/example"));
        Files.writeString(testSources.resolve("greeting_consumer.py"), """
            from jakarta.inject import Singleton
            from example import GreetingService

            @Singleton
            class GreetingConsumer:
                def __init__(self, service: GreetingService):
                    self.service = service

                def greet(self, name: str) -> str:
                    return self.service.greet(name)
            """);
        File mainOutput = Files.createDirectories(temporaryDirectory.resolve("classes/main")).toFile();
        File testOutput = Files.createDirectories(temporaryDirectory.resolve("classes/test")).toFile();
        PyronautCompiler.builder()
            .pythonSrc(mainSources.getParent().toString())
            .targetDir(mainOutput)
            .build()
            .compile();
        PyronautCompiler.builder()
            .pythonSrc(testSources.getParent().toString())
            .targetDir(testOutput)
            .classpath(List.of(mainOutput))
            .build()
            .compile();

        // the test root is first on the class path this time
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{testOutput.toURI().toURL(), mainOutput.toURI().toURL()})) {
            Class<?> consumer = classLoader.loadClass("example.GreetingConsumer");
            try (ApplicationContext context = ApplicationContext.builder().classLoader(classLoader).build().start()) {
                Object bean = context.getBean(consumer);
                assertEquals("Hello Python", ((ValueCoercible) bean).asPolyglotValue().invokeMember("greet", "Python").asString());
                org.graalvm.polyglot.Context pythonContext = context.getBean(org.graalvm.polyglot.Context.class);
                // both classes are members of the package whichever root's initialiser the class path serves first
                assertTrue(pythonContext.eval("python", "from example import GreetingService, GreetingConsumer; True").asBoolean());
            }
        }
    }

    /**
     * A module importing a sibling from its own package ({@code from example import GreetingService}) while the
     * package initialises, with both modules in one root: the members module binding them is still being imported
     * when the sibling import runs, whichever of the two modules it imports first.
     */
    @ParameterizedTest
    @CsvSource({"a_service, b_consumer", "z_service, a_consumer"})
    void aModuleImportsASiblingFromItsOwnPackageInASingleRoot(String serviceModule, String consumerModule) throws Exception {
        Path sources = Files.createDirectories(temporaryDirectory.resolve("src/main/python/example"));
        Files.writeString(sources.resolve(serviceModule + ".py"), """
            from jakarta.inject import Singleton

            @Singleton
            class GreetingService:
                def greet(self, name: str) -> str:
                    return f"Hello {name}"
            """);
        Files.writeString(sources.resolve(consumerModule + ".py"), """
            from jakarta.inject import Singleton
            from example import GreetingService

            @Singleton
            class GreetingConsumer:
                def __init__(self, service: GreetingService):
                    self.service = service

                def greet(self, name: str) -> str:
                    return self.service.greet(name)
            """);
        File output = Files.createDirectories(temporaryDirectory.resolve("classes/main")).toFile();
        PyronautCompiler.builder()
            .pythonSrc(sources.getParent().toString())
            .targetDir(output)
            .build()
            .compile();

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{output.toURI().toURL()})) {
            Class<?> consumer = classLoader.loadClass("example.GreetingConsumer");
            try (ApplicationContext context = ApplicationContext.builder().classLoader(classLoader).build().start()) {
                Object bean = context.getBean(consumer);
                assertEquals("Hello Python", ((ValueCoercible) bean).asPolyglotValue().invokeMember("greet", "Python").asString());
            }
        }
    }

    @Test
    void aStarImportOfTheMainRootPackageBindsItsClasses() throws Exception {
        Path mainSources = Files.createDirectories(temporaryDirectory.resolve("src/main/python/example"));
        Files.writeString(mainSources.resolve("greeting_service.py"), """
            from jakarta.inject import Singleton

            @Singleton
            class GreetingService:
                def greet(self, name: str) -> str:
                    return f"Hello {name}"
            """);
        // a star import of the main package from another package: the compiled classes of the main root are
        // what the star import binds, so the parameter type is the generated class, not Object
        Path testSources = Files.createDirectories(temporaryDirectory.resolve("src/test/python/other"));
        Files.writeString(testSources.resolve("greeting_consumer.py"), """
            from jakarta.inject import Singleton
            from example import *

            @Singleton
            class GreetingConsumer:
                def __init__(self, service: GreetingService):
                    self.service = service

                def greet(self, name: str) -> str:
                    return self.service.greet(name)
            """);
        File mainOutput = Files.createDirectories(temporaryDirectory.resolve("classes/main")).toFile();
        File testOutput = Files.createDirectories(temporaryDirectory.resolve("classes/test")).toFile();
        PyronautCompiler.builder()
            .pythonSrc(mainSources.getParent().toString())
            .targetDir(mainOutput)
            .build()
            .compile();
        PyronautCompiler.builder()
            .pythonSrc(testSources.getParent().toString())
            .targetDir(testOutput)
            .classpath(List.of(mainOutput))
            .build()
            .compile();

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{mainOutput.toURI().toURL(), testOutput.toURI().toURL()})) {
            Class<?> service = classLoader.loadClass("example.GreetingService");
            Class<?> consumer = classLoader.loadClass("other.GreetingConsumer");
            List<List<Class<?>>> constructors = Arrays.stream(consumer.getDeclaredConstructors())
                .map(constructor -> List.<Class<?>>of(constructor.getParameterTypes()))
                .toList();
            assertTrue(constructors.contains(List.of(service)), "constructor parameter types: " + constructors);

            try (ApplicationContext context = ApplicationContext.builder().classLoader(classLoader).build().start()) {
                Object bean = context.getBean(consumer);
                assertEquals("Hello Python", ((ValueCoercible) bean).asPolyglotValue().invokeMember("greet", "Python").asString());
            }
        }
    }
    /**
     * The shim package of a Java package both roots import from: its {@code __all__} lists the members of
     * both contributions before the subpackages of either, whichever way the members modules sort by name.
     */
    @Test
    void theSharedShimPackageListsTheMembersOfBothRootsBeforeTheirSubpackages() throws Exception {
        Path mainSources = Files.createDirectories(temporaryDirectory.resolve("src/main/python/example"));
        // the main root contributes the subpackage micronaut.core.convert.value
        Files.writeString(mainSources.resolve("values_service.py"), """
            from jakarta.inject import Singleton
            from micronaut.core.convert.value import ConvertibleValues

            @Singleton
            class ValuesService:
                def empty(self) -> object:
                    return ConvertibleValues.empty()
            """);
        Path testSources = Files.createDirectories(temporaryDirectory.resolve("src/test/python/example"));
        // the test root contributes the member micronaut.core.convert.ConversionContext
        Files.writeString(testSources.resolve("context_consumer.py"), """
            from jakarta.inject import Singleton
            from micronaut.core.convert import ConversionContext

            @Singleton
            class ContextConsumer:
                def default_context(self) -> object:
                    return ConversionContext.DEFAULT
            """);
        File mainOutput = Files.createDirectories(temporaryDirectory.resolve("classes/main")).toFile();
        File testOutput = Files.createDirectories(temporaryDirectory.resolve("classes/test")).toFile();
        PyronautCompiler.builder()
            .pythonSrc(mainSources.getParent().toString())
            .targetDir(mainOutput)
            .build()
            .compile();
        PyronautCompiler.builder()
            .pythonSrc(testSources.getParent().toString())
            .targetDir(testOutput)
            .classpath(List.of(mainOutput))
            .build()
            .compile();

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{testOutput.toURI().toURL(), mainOutput.toURI().toURL()})) {
            try (ApplicationContext context = ApplicationContext.builder().classLoader(classLoader).build().start()) {
                org.graalvm.polyglot.Context pythonContext = context.getBean(org.graalvm.polyglot.Context.class);
                assertEquals("ConversionContext,value", pythonContext.eval("python", """
                    import micronaut.core.convert
                    ','.join(micronaut.core.convert.__all__)
                    """).asString());
                // the subpackage is there, and importing the package did not import it
                assertEquals("False", pythonContext.eval("python", """
                    import sys
                    str('micronaut.core.convert.value' in sys.modules)
                    """).asString());
                assertTrue(pythonContext.eval("python", """
                    from micronaut.core.convert import value
                    value.ConvertibleValues.empty().isEmpty()
                    """).asBoolean());
            }
        }
    }
}
