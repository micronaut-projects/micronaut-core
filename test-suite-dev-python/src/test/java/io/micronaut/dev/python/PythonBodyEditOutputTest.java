package io.micronaut.dev.python;

import io.micronaut.dev.change.ChangeSet;
import io.micronaut.dev.change.OutputSnapshot;
import io.micronaut.dev.compile.CompilationResult;
import io.micronaut.dev.compile.PythonSourceCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What patching Python in place rests on: an edit of bodies alone leaves the generated classes byte for
 * byte the same and changes only the module's source and its bytecode, while an edit the generated
 * classes reflect changes them, so that it starts a new generation.
 */
class PythonBodyEditOutputTest {

    private static final String SRC = "META-INF/GRAALPY-VFS/micronaut-application/src/";

    @TempDir
    Path project;

    @Test
    void bodyEditsLeaveTheGeneratedClassesAsTheyWereAndStructuralEditsChangeThem() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        fixture.writePython("app/hello.py", """
            from micronaut.http.annotation import Controller, Get


            def helper():
                return 1


            @Controller("/hello")
            class HelloController:
                def __init__(self):
                    self.count = 0

                @Get
                def index(self) -> str:
                    return "alpha"

                @Get("/untyped")
                def untyped(self):
                    return "alpha"
            """);
        fixture.writePython("app/book.py", """
            from micronaut.core.annotation import Introspected


            @Introspected
            class Book:
                def __init__(self, title: str):
                    self.title = title
            """);
        List<Edit> edits = List.of(
            body("a literal a typed method returns", "app/hello.py", "return \"alpha\"\n\n    @Get(\"/untyped\")", "return \"gamma\"\n\n    @Get(\"/untyped\")"),
            body("a literal an untyped method returns", "app/hello.py", "def untyped(self):\n        return \"alpha\"", "def untyped(self):\n        return \"beta\""),
            body("the type of what an untyped method returns", "app/hello.py", "def untyped(self):\n        return \"beta\"", "def untyped(self):\n        return 1"),
            body("a local variable", "app/hello.py", "return \"gamma\"", "word = 'gam' + 'ma'\n        return word"),
            body("an attribute set outside __init__", "app/hello.py", "word = 'gam' + 'ma'", "self.count += 1\n        word = 'gam' + 'ma'"),
            body("a module function added", "app/hello.py", "def helper():", "def added():\n    return 2\n\n\ndef helper():"),
            body("a default of a module function", "app/hello.py", "def helper():", "def helper(value=2):"),
            body("a docstring", "app/hello.py", "def untyped(self):", "def untyped(self):\n        \"\"\"Untyped.\"\"\""),
            body("a call of a Java superclass method", "app/greeter.py", "return \"one\"", "return super().toString()[:0] + \"one\""),
            structural("an untyped method that becomes a generator", "app/hello.py", "def untyped(self):\n        \"\"\"Untyped.\"\"\"\n        return 1", "def untyped(self):\n        \"\"\"Untyped.\"\"\"\n        yield 1"),
            body("an attribute a controller's __init__ sets", "app/hello.py", "self.count = 0", "self.count = 0\n        self.extra = 'x'"),
            body("an untyped attribute an introspected class's __init__ sets", "app/book.py", "self.title = title", "self.title = title\n        self.pages = 0"),
            body("a typed attribute an introspected class's __init__ sets", "app/book.py", "self.pages = 0", "self.pages: int = 0"),
            structural("a field an introspected class declares", "app/book.py", "class Book:\n", "class Book:\n    isbn: str\n\n"),
            structural("a route added", "app/hello.py", "    @Get(\"/untyped\")", "    @Get(\"/added\")\n    def added_route(self) -> str:\n        return \"added\"\n\n    @Get(\"/untyped\")")
        );
        List<String> unexpected = new ArrayList<>();
        PythonSourceCompiler compiler = new PythonSourceCompiler();
        try {
            CompilationResult full = compiler.compile(fixture.request(Set.of(), Set.of()).asFull());
            assertTrue(full.isSuccess(), full.diagnostics().toString());
            for (Edit edit : edits) {
                OutputSnapshot before = OutputSnapshot.of(List.of(fixture.classOutput()));
                Path file = fixture.python().resolve(edit.file());
                String source = Files.readString(file);
                assertTrue(source.contains(edit.from()), edit.description() + ": no match in\n" + source);
                Files.writeString(file, source.replace(edit.from(), edit.to()));
                CompilationResult result = compiler.compile(fixture.request(Set.of(file), Set.of()));
                assertTrue(result.isSuccess(), edit.description() + ": " + result.diagnostics());
                ChangeSet changes = before.diff(OutputSnapshot.of(List.of(fixture.classOutput())));
                boolean onlyModule = changes.removedResources().isEmpty() && !changes.changedResources().isEmpty()
                    && changes.changedResources().stream().allMatch(resource -> resource.startsWith(SRC + "app/")
                    && (resource.endsWith(".py") || resource.endsWith(".pyc")));
                if (edit.bodyOnly() != (changes.classes().isEmpty() && onlyModule)) {
                    unexpected.add(edit.description() + ": " + changes);
                }
            }
        } finally {
            compiler.close();
        }
        assertEquals(List.of(), unexpected);
    }

    private static Edit body(String description, String file, String from, String to) {
        return new Edit(description, file, from, to, true);
    }

    private static Edit structural(String description, String file, String from, String to) {
        return new Edit(description, file, from, to, false);
    }

    private record Edit(String description, String file, String from, String to, boolean bodyOnly) {
    }
}
