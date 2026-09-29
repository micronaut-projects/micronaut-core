package io.micronaut.dev.change

import io.micronaut.dev.compile.CompilationRequest
import io.micronaut.dev.compile.JavacSourceCompiler
import io.micronaut.dev.compile.SourceKind
import io.micronaut.dev.compile.SourceRoot
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class ClassStructureSpec extends Specification {

    @TempDir
    Path project

    void "only bodies changed: #title"() {
        expect:
        ClassStructure.bodyOnlyChange(compile(before), compile(after)) == expected

        where:
        title                        | before                                                                        | after                                                                                                   | expected
        "a return value"             | 'class A { String s() { return "one"; } }'                                    | 'class A { String s() { return "two"; } }'                                                             | true
        "a lambda body"              | 'class A { java.util.function.Supplier<String> s() { return () -> "one"; } }' | 'class A { java.util.function.Supplier<String> s() { return () -> "one" + "!"; } }'                    | true
        "a new local and a loop"     | 'class A { int n() { return 1; } }'                                           | 'class A { int n() { int t = 0; for (int i = 0; i < 3; i++) { t += i; } return t; } }'                 | true
        "the same source"            | 'class A { int n() { return 1; } }'                                           | 'class A { int n() { return 1; } }'                                                                    | true
        "a new method"               | 'class A { int n() { return 1; } }'                                           | 'class A { int n() { return 1; } int m() { return 2; } }'                                              | false
        "a changed signature"        | 'class A { int n() { return 1; } }'                                           | 'class A { long n() { return 1; } }'                                                                   | false
        "a new field"                | 'class A { int n() { return 1; } }'                                           | 'class A { int f; int n() { return f; } }'                                                             | false
        "a changed annotation value" | '@Deprecated(since = "1") class A { int n() { return 1; } }'                  | '@Deprecated(since = "2") class A { int n() { return 1; } }'                                            | false
        "a changed constant"         | 'class A { static final String V = "a"; int n() { return 1; } }'              | 'class A { static final String V = "b"; int n() { return 1; } }'                                       | false
        "a changed static initializer" | 'class A { static final Integer V = 1; int n() { return V; } }'              | 'class A { static final Integer V = 2; int n() { return V; } }'                                       | false
        "a new interface"            | 'class A { int n() { return 1; } }'                                           | 'class A implements java.io.Serializable { int n() { return 1; } }'                                     | false
    }

    private byte[] compile(String source) {
        Path dir = Files.createTempDirectory(project, "v")
        Path src = Files.createDirectories(dir.resolve("src"))
        Files.writeString(src.resolve("A.java"), source)
        Path out = dir.resolve("out")
        def result = new JavacSourceCompiler().compile(new CompilationRequest(SourceKind.JAVA, [new SourceRoot(SourceKind.JAVA, src)], [] as Set, [] as Set, true, [], [], out, dir.resolve("gen"), []).asFull())
        assert result.isSuccess() : result.diagnostics()
        Files.readAllBytes(out.resolve("A.class"))
    }
}
