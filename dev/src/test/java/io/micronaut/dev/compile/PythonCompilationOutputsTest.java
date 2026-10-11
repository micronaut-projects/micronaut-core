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
package io.micronaut.dev.compile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file handling of the embedded Python compiler, which runs without the Pyronaut compiler: the compilations
 * themselves are tested with GraalPy in test-suite-dev-python.
 */
class PythonCompilationOutputsTest {

    @TempDir
    Path directory;

    @Test
    void withoutThePyronautCompilerTheCompilerIsUnavailable() {
        PythonSourceCompiler compiler = new PythonSourceCompiler();
        assertEquals(Set.of(SourceKind.PYTHON), compiler.kinds());
        assertEquals(Set.of(SourceKind.JAVA), compiler.jointKinds());
        assertFalse(compiler.isAvailable());
        CompilationRequest request = request(List.of(), List.of());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> compiler.compile(request));
        assertTrue(e.getMessage().contains("micronaut-inject-python"), e.getMessage());
        compiler.close();
    }

    @Test
    void theTopLevelClassesOfClassFiles() {
        assertEquals(Set.of("app.Foo", "Top"), PythonCompilation.topLevelClasses(List.of(
            "app/Foo.class", "app/Foo$Inner.class", "app/$Foo$Definition.class", "Top.class", "META-INF/versions/Foo.class",
            "app/module-info.class", "app/package-info.class", "app/readme.txt")));
    }

    @Test
    void theOutputsOfABuildByPyronautAreKnownByTheirShape() throws IOException {
        Path classes = directory.resolve("classes");
        write(classes, "META-INF/GRAALPY-VFS/src/app/greeter.py", "x");
        write(classes, "pyronaut_application/Application.class", "x");
        write(classes, "app/GreeterStub.java", "x");
        write(classes, "app/GreeterStub.class", "x");
        write(classes, "app/$GreeterStub$Definition.class", "x");
        write(classes, "META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/app.$GreeterStub$Definition", "");
        write(classes, "META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/app.$Other$Definition", "");
        write(classes, "META-INF/other.txt", "x");
        write(classes, "app/Other.class", "x");

        assertEquals(Set.of(
            "META-INF/GRAALPY-VFS/src/app/greeter.py",
            "pyronaut_application/Application.class",
            "app/GreeterStub.java",
            "app/GreeterStub.class",
            "app/$GreeterStub$Definition.class",
            "META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/app.$GreeterStub$Definition"), PythonCompilation.builtOutputs(classes));
        assertEquals(Set.of(), PythonCompilation.builtOutputs(directory.resolve("missing")));
    }

    @Test
    void theOutputsAreMirroredAndWhatAnotherToolWroteIsKept() throws IOException {
        Path work = directory.resolve("classes-python");
        Path classes = directory.resolve("classes");
        Path generated = directory.resolve("generated");
        write(work, "app/Greeter.class", "one");
        write(work, "app/GreeterStub.java", "stub");
        write(work, "META-INF/GRAALPY-VFS/src/app/greeter.py", "print(1)");
        write(classes, "app/Foreign.class", "foreign");

        PythonCompilation.Mirrored first = PythonCompilation.mirror(work, classes, generated);
        assertEquals(List.of("META-INF/GRAALPY-VFS/src/app/greeter.py", "app/Greeter.class", "app/GreeterStub.java"), first.changed());
        assertEquals(List.of(), first.removed());
        assertEquals("one", Files.readString(classes.resolve("app/Greeter.class")));
        // a generated source goes to the generated sources, not beside the classes
        assertEquals("stub", Files.readString(generated.resolve("app/GreeterStub.java")));
        assertFalse(Files.exists(classes.resolve("app/GreeterStub.java")));

        // nothing changed: nothing to do
        PythonCompilation.Mirrored again = PythonCompilation.mirror(work, classes, generated);
        assertEquals(List.of(), again.changed());
        assertEquals(List.of(), again.removed());

        // the class goes and the module changes
        Files.delete(work.resolve("app/Greeter.class"));
        write(work, "META-INF/GRAALPY-VFS/src/app/greeter.py", "print(2)");
        PythonCompilation.Mirrored next = PythonCompilation.mirror(work, classes, generated);
        assertEquals(List.of("META-INF/GRAALPY-VFS/src/app/greeter.py"), next.changed());
        assertEquals(List.of("app/Greeter.class"), next.removed());
        assertEquals(Set.of(classes.resolve("app/Greeter.class")), next.removedOutputs());
        assertFalse(Files.exists(classes.resolve("app/Greeter.class")));
        assertEquals("print(2)", Files.readString(classes.resolve("META-INF/GRAALPY-VFS/src/app/greeter.py")));
        assertEquals("foreign", Files.readString(classes.resolve("app/Foreign.class")));

        // the class another tool wrote is resolvable beside the output; the compiler's own outputs are not in it
        Path foreign = PythonCompilation.foreignClasses(classes);
        assertEquals(directory.resolve("classes-python-foreign"), foreign);
        assertArrayEquals("foreign".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(foreign.resolve("app/Foreign.class")));
        assertFalse(Files.exists(foreign.resolve("META-INF")));
        Files.delete(classes.resolve("app/Foreign.class"));
        assertNull(PythonCompilation.foreignClasses(classes));
    }

    @Test
    void theClasspathLeavesOutTheOutputsAndTheRootsAreTheDirectoriesOfTheirKind() throws IOException {
        Path python = Files.createDirectories(directory.resolve("src/main/python"));
        Path java = Files.createDirectories(directory.resolve("src/main/java"));
        Path library = directory.resolve("lib.jar");
        Path work = directory.resolve("classes-python");
        Path foreign = directory.resolve("classes-python-foreign");
        CompilationRequest request = request(
            List.of(new SourceRoot(SourceKind.PYTHON, python), new SourceRoot(SourceKind.JAVA, java), new SourceRoot(SourceKind.PYTHON, directory.resolve("missing"))),
            List.of(library, directory.resolve("classes"), work, foreign));

        assertEquals(List.of(library, foreign), PythonCompilation.classpath(request, work, foreign));
        // without foreign classes, the class output and the work directory alone are left out
        assertEquals(List.of(library), PythonCompilation.classpath(request(List.of(), List.of(library, directory.resolve("classes"), work)), work, null));
        assertEquals(List.of(python.toString()), PythonCompilation.roots(request, SourceKind.PYTHON));
        assertEquals(List.of(java.toString()), PythonCompilation.roots(request, SourceKind.JAVA));
    }

    private CompilationRequest request(List<SourceRoot> roots, List<Path> classpath) {
        return new CompilationRequest(SourceKind.PYTHON, roots, Set.of(), Set.of(), true, classpath, List.of(),
            directory.resolve("classes"), directory.resolve("generated"), List.of());
    }

    private static void write(Path root, String file, String content) throws IOException {
        Path path = root.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
