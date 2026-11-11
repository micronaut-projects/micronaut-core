package io.micronaut.dev.change

import io.micronaut.dev.compile.CompilationRequest
import io.micronaut.dev.compile.JavacSourceCompiler
import io.micronaut.dev.compile.SourceKind
import io.micronaut.dev.compile.SourceRoot
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class ClassStructureConstructorSpec extends Specification {

    @TempDir
    Path project

    void "a change an existing instance would never see is structural: #title"() {
        expect:
        !ClassStructure.bodyOnlyChange(compile(before), compile(after))

        where:
        title                                  | before                                                              | after
        "a constructor body"                   | 'class A { final String s; A() { s = "one"; } String s() { return s; } }' | 'class A { final String s; A() { s = "two"; } String s() { return s; } }'
        "an instance field initializer"        | 'class A { String s = "one".trim(); String s() { return s; } }'     | 'class A { String s = "two".trim(); String s() { return s; } }'
    }

    void "a method body change next to an unchanged constructor is a body change"() {
        expect:
        ClassStructure.bodyOnlyChange(
                compile('class A { final String s; A() { s = "one"; } String s() { return s; } }'),
                compile('class A { final String s; A() { s = "one"; } String s() { return s.trim(); } }'))
    }

    void "a method body change that moves the lines of the constructor is a body change"() {
        expect:
        ClassStructure.bodyOnlyChange(
                compile('class A {\n String t() { return "t"; }\n final String s;\n A() {\n s = "one";\n }\n}'),
                compile('class A {\n String t() {\n String x = "t";\n return x;\n }\n final String s;\n A() {\n s = "one";\n }\n}'))
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
