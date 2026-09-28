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
package io.micronaut.dev.compile

import io.micronaut.dev.compile.processor.MarkedProcessor
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class JavacSourceCompilerSpec extends Specification {

    @TempDir
    Path project

    Path src
    Path out
    Path generated
    JavacSourceCompiler compiler = new JavacSourceCompiler()

    def setup() {
        src = Files.createDirectories(project.resolve("src/main/java"))
        out = project.resolve("build/classes")
        generated = project.resolve("build/generated")
        write("example/Base.java", "package example; public class Base { public String name() { return \"base\"; } }")
        write("example/Derived.java", "package example; public class Derived extends Base { public String name() { return \"derived:\" + super.name(); } }")
        write("example/Alone.java", "package example; public class Alone { public int value() { return 1; } }")
    }

    void "a full compilation compiles every source and the index knows who references whom"() {
        when:
        def result = compiler.compile(request().asFull())

        then:
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources.size() == 3
        Files.exists(out.resolve("example/Base.class"))
        Files.exists(out.resolve("example/Derived.class"))
        Files.exists(out.resolve("example/Alone.class"))

        when:
        def index = ClassDependencyIndex.scan(out)

        then:
        index.classes() == ["example.Base", "example.Derived", "example.Alone"] as Set
        index.referencesOf("example.Derived").contains("example.Base")
        index.dependentsOf("example.Base") == ["example.Derived"] as Set
        index.transitiveDependentsOf(["example.Base"] as Set) == ["example.Derived"] as Set
        index.dependentsOf("example.Alone").isEmpty()
    }

    void "an incremental compilation recompiles the changed source and its dependents only"() {
        given:
        compiler.compile(request().asFull())
        long aloneBefore = Files.getLastModifiedTime(out.resolve("example/Alone.class")).toMillis()

        when: "the base class changes shape"
        Thread.sleep(20)
        Path base = write("example/Base.java", "package example; public class Base { public String name() { return \"changed\"; } public int extra() { return 2; } }")
        def result = compiler.compile(request([base] as Set, [] as Set))

        then: "the base and the derived class were compiled, the unrelated one was not touched"
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString().toSet() == ["Base.java", "Derived.java"] as Set
        Files.getLastModifiedTime(out.resolve("example/Alone.class")).toMillis() == aloneBefore
        loadAndCall("example.Derived", "name") == "derived:changed"
    }

    void "a deleted source has its outputs removed, including generated companions and index entries"() {
        given:
        compiler.compile(request().asFull())
        Path index = Files.createDirectories(out.resolve("META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference"))
        Files.writeString(index.resolve('example.$Alone$Definition$Reference'), "")
        Files.writeString(index.resolve('example.$Base$Definition$Reference'), "")
        Files.writeString(out.resolve('example/$Alone$Definition.class'), "x")
        Files.writeString(out.resolve('example/Alone$Inner.class'), "x")
        Files.writeString(out.resolve('example/AloneToo.class'), "x")
        Path alone = src.resolve("example/Alone.java")
        Files.delete(alone)

        when:
        def result = compiler.compile(request([] as Set, [alone] as Set))

        then:
        result.status == CompilationResult.Status.NOTHING_TO_DO
        !Files.exists(out.resolve("example/Alone.class"))
        !Files.exists(out.resolve('example/$Alone$Definition.class'))
        !Files.exists(out.resolve('example/Alone$Inner.class'))
        !Files.exists(index.resolve('example.$Alone$Definition$Reference'))
        Files.exists(out.resolve("example/AloneToo.class"))
        Files.exists(index.resolve('example.$Base$Definition$Reference'))
        result.removedOutputs.size() == 4
    }

    void "a recompiled source loses the outputs it no longer produces"() {
        given:
        write("example/Outer.java", "package example; public class Outer { public static class Inner { } public Object make() { return new Object() { }; } }")
        compiler.compile(request().asFull())
        Files.exists(out.resolve('example/Outer$Inner.class'))
        Files.exists(out.resolve('example/Outer$1.class'))

        when: "the nested classes are dropped"
        Path outer = write("example/Outer.java", "package example; public class Outer { public Object make() { return this; } }")
        def result = compiler.compile(request([outer] as Set, [] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        Files.exists(out.resolve("example/Outer.class"))
        !Files.exists(out.resolve('example/Outer$Inner.class'))
        !Files.exists(out.resolve('example/Outer$1.class'))
        result.removedOutputs*.fileName*.toString().toSet() == ['Outer$Inner.class', 'Outer$1.class'] as Set
        Files.exists(out.resolve("example/Alone.class"))
    }

    void "a failed compilation keeps the outputs of the sources deleted alongside it"() {
        given:
        compiler.compile(request().asFull())
        Path alone = src.resolve("example/Alone.java")
        Files.delete(alone)

        when: "a deletion arrives with an edit that does not compile"
        Path base = write("example/Base.java", "package example; public class Base { public String name() { return 1; } }")
        def result = compiler.compile(request([base] as Set, [alone] as Set))

        then: "the output is untouched, the deleted class included, and nothing is reported removed"
        result.status == CompilationResult.Status.FAILED
        Files.exists(out.resolve("example/Alone.class"))
        result.removedOutputs.isEmpty()

        when: "the edit is fixed"
        base = write("example/Base.java", "package example; public class Base { public String name() { return \"fixed\"; } }")
        result = compiler.compile(request([base] as Set, [alone] as Set))

        then: "the deletion is applied with the successful compilation"
        result.status == CompilationResult.Status.SUCCESS
        !Files.exists(out.resolve("example/Alone.class"))
        result.removedOutputs*.fileName*.toString() == ["Alone.class"]
    }

    void "the index sees the classes nested in a generic signature"() {
        given:
        write("example/Holder.java", "package example; import java.util.List; public class Holder { public List<Base> bases; public java.util.Map<String, List<Alone>> more() { return null; } }")
        compiler.compile(request().asFull())

        when:
        def index = ClassDependencyIndex.scan(out)

        then:
        index.referencesOf("example.Holder").containsAll(["example.Base", "example.Alone", "java.util.List", "java.util.Map"])
        index.dependentsOf("example.Alone") == ["example.Holder"] as Set
    }

    void "a deleted class is hidden from its dependents, which fail to compile rather than link against it"() {
        given:
        compiler.compile(request().asFull())
        Path base = src.resolve("example/Base.java")
        Files.delete(base)

        when: "the base class goes while the derived one still extends it"
        def result = compiler.compile(request([] as Set, [base] as Set))

        then: "the derived class cannot compile against the stale class file and the output is untouched"
        result.status == CompilationResult.Status.FAILED
        result.errors().first().file().fileName.toString() == "Derived.java"
        Files.exists(out.resolve("example/Base.class"))
        Files.exists(out.resolve("example/Derived.class"))

        when: "the derived class is fixed"
        Path derived = write("example/Derived.java", "package example; public class Derived { public String name() { return \"alone\"; } }")
        result = compiler.compile(request([derived] as Set, [base] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        !Files.exists(out.resolve("example/Base.class"))
        loadAndCall("example.Derived", "name") == "alone"
    }

    void "a source declaring several top-level classes is tracked whole"() {
        given:
        write("example/Pair.java", "package example; public class Pair { } class Helper { public String help() { return \"one\"; } }")
        write("example/UsesHelper.java", "package example; public class UsesHelper { public String go() { return new Helper().help(); } }")
        compiler.compile(request().asFull())
        Files.exists(out.resolve("example/Helper.class"))
        Files.readString(out.resolveSibling("classes" + SourceIndex.MAPPING_SUFFIX)).contains("example.Pair,example.Helper")

        when: "the helper changes inside the pair source"
        Path pair = write("example/Pair.java", "package example; public class Pair { } class Helper { public String help() { return \"two\"; } }")
        def result = compiler.compile(request([pair] as Set, [] as Set))

        then: "the user of the helper was recompiled too"
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString().toSet() == ["Pair.java", "UsesHelper.java"] as Set
        loadAndCall("example.UsesHelper", "go") == "two"

        when: "the pair source is deleted along with its user"
        Files.delete(pair)
        Path uses = src.resolve("example/UsesHelper.java")
        Files.delete(uses)
        result = compiler.compile(request([] as Set, [pair, uses] as Set))

        then: "the helper class went with it"
        result.status == CompilationResult.Status.NOTHING_TO_DO
        !Files.exists(out.resolve("example/Helper.class"))
        !Files.exists(out.resolve("example/Pair.class"))
        !Files.readString(out.resolveSibling("classes" + SourceIndex.MAPPING_SUFFIX)).contains("Pair.java")
    }

    void "a class a source stops declaring cannot be linked against by a recompiled dependent"() {
        given:
        write("example/Pair.java", "package example; public class Pair { } class Helper { public String help() { return \"one\"; } }")
        write("example/UsesHelper.java", "package example; public class UsesHelper { public String go() { return new Helper().help(); } }")
        compiler.compile(request().asFull())

        when: "the helper is dropped from the pair source"
        Path pair = write("example/Pair.java", "package example; public class Pair { }")
        def result = compiler.compile(request([pair] as Set, [] as Set))

        then: "the user of the helper fails to compile instead of linking against the old class file"
        result.status == CompilationResult.Status.FAILED
        result.errors().first().file().fileName.toString() == "UsesHelper.java"
        Files.exists(out.resolve("example/Helper.class"))
    }

    void "a full compilation replaces what the compiler produced before but keeps the resources beside it"() {
        given: "an output with a class whose source went away unseen, and a resource the build copied in"
        compiler.compile(request().asFull())
        Files.delete(src.resolve("example/Alone.java"))
        Files.writeString(out.resolve("application.yml"), "micronaut: {}")
        Path index = Files.createDirectories(out.resolve("META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference"))
        Files.writeString(index.resolve('example.$Alone$Definition$Reference'), "")

        when:
        def result = compiler.compile(request().asFull())

        then:
        result.status == CompilationResult.Status.SUCCESS
        !Files.exists(out.resolve("example/Alone.class"))
        !Files.exists(index.resolve('example.$Alone$Definition$Reference'))
        Files.exists(out.resolve("example/Base.class"))
        Files.readString(out.resolve("application.yml")) == "micronaut: {}"
        result.removedOutputs*.fileName*.toString().toSet() == ["Alone.class", 'example.$Alone$Definition$Reference'] as Set
    }

    void "a change to a class declaring constants recompiles every source"() {
        given:
        write("example/Constants.java", "package example; public class Constants { public static final String VALUE = \"one\"; }")
        write("example/Reader.java", "package example; public class Reader { public String read() { return Constants.VALUE; } }")
        compiler.compile(request().asFull())

        expect: "the index knows which class declares the constants javac inlines"
        ClassDependencyIndex.scan(out).declaresConstants("example.Constants")
        !ClassDependencyIndex.scan(out).declaresConstants("example.Reader")

        when:
        Path constants = write("example/Constants.java", "package example; public class Constants { public static final String VALUE = \"two\"; }")
        def result = compiler.compile(request([constants] as Set, [] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources.size() == 5
        loadAndCall("example.Reader", "read") == "two"
    }

    void "a full compilation without a source left clears the compiler outputs"() {
        given:
        compiler.compile(request().asFull())
        Files.writeString(out.resolve("application.yml"), "micronaut: {}")
        ["Base", "Derived", "Alone"].each { Files.delete(src.resolve("example/${it}.java")) }

        when:
        def result = compiler.compile(request().asFull())

        then:
        result.status == CompilationResult.Status.NOTHING_TO_DO
        !Files.exists(out.resolve("example/Base.class"))
        !Files.exists(out.resolve("example/Derived.class"))
        !Files.exists(out.resolve("example/Alone.class"))
        !Files.exists(out.resolveSibling("classes" + SourceIndex.MAPPING_SUFFIX))
        Files.exists(out.resolve("application.yml"))
        result.removedOutputs.size() == 4
    }

    void "what a processor generates for a source goes when the source stops asking for it, and with the source"() {
        given: "a marked class, compiled with the processor on the path"
        write("example/Alone.java", "package example; @io.micronaut.dev.compile.processor.Marked public class Alone { public int value() { return 1; } }")
        def result = compiler.compile(request([] as Set, [] as Set, processorPath()).asFull())

        expect: "the companion source, its class and the resource were produced and credited to the source"
        result.status == CompilationResult.Status.SUCCESS
        Files.exists(generated.resolve("example/AloneGenerated.java"))
        Files.exists(out.resolve("example/AloneGenerated.class"))
        Files.readString(out.resolve("META-INF/marked/Alone")) == "Alone"
        Files.readString(out.resolveSibling("classes" + SourceIndex.MAPPING_SUFFIX)).contains("AloneGenerated")

        when: "the mark is removed"
        Path alone = write("example/Alone.java", "package example; public class Alone { public int value() { return 1; } }")
        result = compiler.compile(request([alone] as Set, [] as Set, processorPath()))

        then: "the companion and the resource are gone, the class stays"
        result.status == CompilationResult.Status.SUCCESS
        !Files.exists(generated.resolve("example/AloneGenerated.java"))
        !Files.exists(out.resolve("example/AloneGenerated.class"))
        !Files.exists(out.resolve("META-INF/marked/Alone"))
        Files.exists(out.resolve("example/Alone.class"))
        result.removedOutputs*.fileName*.toString().toSet() == ["AloneGenerated.java", "AloneGenerated.class", "Alone"] as Set

        when: "the mark comes back, then the source is deleted"
        write("example/Alone.java", "package example; @io.micronaut.dev.compile.processor.Marked public class Alone { public int value() { return 1; } }")
        compiler.compile(request([alone] as Set, [] as Set, processorPath()))
        Files.exists(out.resolve("META-INF/marked/Alone"))
        Files.delete(alone)
        result = compiler.compile(request([] as Set, [alone] as Set, processorPath()))

        then: "everything produced for it went with it"
        result.status == CompilationResult.Status.NOTHING_TO_DO
        !Files.exists(generated.resolve("example/AloneGenerated.java"))
        !Files.exists(out.resolve("example/AloneGenerated.class"))
        !Files.exists(out.resolve("META-INF/marked/Alone"))
        !Files.exists(out.resolve("example/Alone.class"))
    }

    void "generated sources are staged and only promoted with a successful compilation"() {
        given: "a generated source left by a previous compilation"
        compiler.compile(request().asFull())
        Files.createDirectories(generated)
        Files.writeString(generated.resolve("Stale.java"), "stale")

        when: "a full compilation succeeds"
        def result = compiler.compile(request().asFull())

        then: "the stale generated source is gone"
        result.status == CompilationResult.Status.SUCCESS
        !Files.exists(generated.resolve("Stale.java"))
        !Files.exists(generated.resolveSibling("generated.micronaut-dev-staging"))
    }

    void "a promotion that fails is rolled back"() {
        given:
        compiler.compile(request().asFull())
        byte[] derivedBefore = Files.readAllBytes(out.resolve("example/Derived.class"))
        Path blocker = out.resolve("example/Base.class")
        Files.delete(blocker)
        Files.createDirectories(blocker)
        Files.writeString(blocker.resolve("blocker"), "a directory where the class file must go")

        when:
        Path base = write("example/Base.java", "package example; public class Base { public String name() { return \"changed\"; } }")
        def result = compiler.compile(request([base] as Set, [] as Set))

        then: "the compilation succeeded but could not be promoted, and the previous output is back"
        result.status == CompilationResult.Status.FAILED
        result.errors().first().message().startsWith("Compilation failed")
        result.compiledSources*.fileName*.toString().toSet() == ["Base.java", "Derived.java"] as Set
        Files.readAllBytes(out.resolve("example/Derived.class")) == derivedBefore
        Files.isDirectory(blocker)
        !Files.exists(out.resolveSibling("classes.micronaut-dev-backup"))
        !Files.exists(out.resolveSibling("classes.micronaut-dev-staging"))
    }

    void "a failed compilation reports the error and leaves the output as it was"() {
        given:
        compiler.compile(request().asFull())
        byte[] before = Files.readAllBytes(out.resolve("example/Base.class"))

        when:
        Path base = write("example/Base.java", "package example; public class Base { public String name() { return 1; } }")
        def result = compiler.compile(request([base] as Set, [] as Set))

        then:
        result.status == CompilationResult.Status.FAILED
        !result.isSuccess()
        result.errors().size() == 1
        result.errors().first().file() == base
        result.errors().first().line() == 1
        result.errors().first().message().contains("incompatible types")
        Files.readAllBytes(out.resolve("example/Base.class")) == before
        !Files.exists(out.resolveSibling("classes.micronaut-dev-staging"))
    }

    void "output names belonging to a class are recognised"() {
        expect:
        JavacSourceCompiler.belongsTo('Foo.class', 'a.Foo', '.class') == false
        JavacSourceCompiler.belongsTo('Foo.class', 'Foo', '.class')
        JavacSourceCompiler.belongsTo('Foo$Bar.class', 'Foo', '.class')
        JavacSourceCompiler.belongsTo('$Foo$Definition.class', 'Foo', '.class')
        JavacSourceCompiler.belongsTo('Foo$Intercepted.class', 'Foo', '.class')
        !JavacSourceCompiler.belongsTo('FooBar.class', 'Foo', '.class')
        JavacSourceCompiler.belongsTo('a.b.$Foo$Definition$Reference', 'a.b.Foo', '')
        !JavacSourceCompiler.belongsTo('a.b.$Foobar$Definition$Reference', 'a.b.Foo', '')
        ClassDependencyIndex.topLevelOf('a.b.$Foo$Definition') == 'a.b.Foo'
        ClassDependencyIndex.topLevelOf('a.b.Foo$Inner$1') == 'a.b.Foo'
        ClassDependencyIndex.topLevelOf('Foo') == 'Foo'
    }

    private Path write(String relative, String content) {
        Path file = src.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        return file
    }

    private CompilationRequest request(Set<Path> changed = [] as Set, Set<Path> deleted = [] as Set, List<Path> processorPath = []) {
        new CompilationRequest(SourceKind.JAVA, [new SourceRoot(SourceKind.JAVA, src)], changed, deleted, false, processorPath, processorPath, out, generated, ["-parameters"])
    }

    /**
     * The test classes and resources, which hold the {@link MarkedProcessor} and its service registration.
     */
    private static List<Path> processorPath() {
        Path classes = Path.of(MarkedProcessor.class.protectionDomain.codeSource.location.toURI())
        Path resources = Path.of(MarkedProcessor.class.getResource("/META-INF/services/javax.annotation.processing.Processor").toURI()).parent.parent.parent
        [classes, resources]
    }

    private Object loadAndCall(String className, String method) {
        URLClassLoader loader = new URLClassLoader([out.toUri().toURL()] as URL[], null)
        try {
            Class<?> type = loader.loadClass(className)
            return type.getMethod(method).invoke(type.getDeclaredConstructor().newInstance())
        } finally {
            loader.close()
        }
    }
}
