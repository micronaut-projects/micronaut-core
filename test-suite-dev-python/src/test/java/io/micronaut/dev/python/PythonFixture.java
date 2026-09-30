package io.micronaut.dev.python;

import io.micronaut.dev.compile.CompilationRequest;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * A Python module with a Java source it extends: {@code app/greeter.py} declares a bean whose class
 * extends the Java {@code base.Greeting}, both compiled into one class output.
 */
record PythonFixture(Path project, Path python, Path java, Path classOutput) {

    static PythonFixture create(Path project) throws IOException {
        PythonFixture fixture = new PythonFixture(project,
            Files.createDirectories(project.resolve("src/main/python")),
            Files.createDirectories(project.resolve("src/main/java")),
            project.resolve("build/classes"));
        fixture.writeGreeting("Hello ");
        fixture.writeGreeter("one");
        return fixture;
    }

    Path greeter() {
        return python.resolve("app/greeter.py");
    }

    Path greeting() {
        return java.resolve("base/Greeting.java");
    }

    void writeGreeter(String name) throws IOException {
        writePython("app/greeter.py", """
            from jakarta.inject import Singleton
            from base import Greeting


            @Singleton
            class PythonGreeter(Greeting):
                def name(self) -> str:
                    return "%s"
            """.formatted(name));
    }

    void writeGreeting(String salutation) throws IOException {
        Files.createDirectories(greeting().getParent());
        Files.writeString(greeting(), """
            package base;

            public abstract class Greeting {
                protected abstract String name();

                public String greet() {
                    return "%s" + name();
                }
            }
            """.formatted(salutation));
    }

    Path writePython(String relative, String source) throws IOException {
        Path file = python.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }

    static List<Path> testClasspath() {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator)).map(Path::of).toList();
    }

    CompilationRequest request(Set<Path> changed, Set<Path> deleted) {
        List<SourceRoot> roots = List.of(new SourceRoot(SourceKind.PYTHON, python), new SourceRoot(SourceKind.JAVA, java));
        return new CompilationRequest(SourceKind.PYTHON, roots, changed, deleted, false, testClasspath(), List.of(), classOutput,
            project.resolve("build/generated"), List.of());
    }
}
