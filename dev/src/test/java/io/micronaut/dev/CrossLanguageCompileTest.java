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
package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A change in one language reaches the sources of another that reference it.
 */
class CrossLanguageCompileTest {

    @TempDir
    Path project;

    @Test
    void aJavaChangeRecompilesTheGroovySourcesThatUseIt() throws Exception {
        Path java = Files.createDirectories(project.resolve("src/main/java/app"));
        Path groovy = Files.createDirectories(project.resolve("src/main/groovy/app"));
        Files.writeString(java.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "CrossLanguageCompileTest"))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Path words = java.resolve("Words.java");
        Files.writeString(words, "package app; public class Words { public static String text() { return \"one\"; } }");
        Path shout = groovy.resolve("Shout.groovy");
        Files.writeString(shout, shout("text"));
        List<String> classpath = List.of(System.getProperty("java.class.path").split(File.pathSeparator));
        Files.write(project.resolve("cp.argfile"), classpath);
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.sources.groovy=src/main/groovy
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.compile.groovy.output=build/classes
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            assertEquals(1, runtime.generation());

            // the Java method the Groovy source calls goes away: the Groovy source is compiled again and fails,
            // rather than keeping bytecode that calls a method that is gone
            Files.writeString(words, "package app; public class Words { public static String words() { return \"two\"; } }");
            runtime.sourcesChanged(SourceKind.JAVA, Set.of(words), Set.of());
            assertTrue(runtime.lastFailure().isPresent());
            assertEquals(SourceKind.GROOVY, runtime.lastFailure().get().kind());
            assertEquals(1, runtime.generation());

            // the Groovy fix reloads both
            Files.writeString(shout, shout("words"));
            runtime.sourcesChanged(SourceKind.GROOVY, Set.of(shout), Set.of());
            ApplicationContext fixed = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertTrue(runtime.lastFailure().isEmpty());
            assertEquals("TWO", fixed.getClassLoader().loadClass("app.Shout").getMethod("shout").invoke(null));
        } finally {
            runtime.close();
        }
    }

    private static String shout(String method) {
        return "package app\n@groovy.transform.CompileStatic\nclass Shout { static String shout() { Words." + method + "().toUpperCase() } }";
    }
}
