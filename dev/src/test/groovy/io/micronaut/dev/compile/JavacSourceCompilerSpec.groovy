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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.dev.compile.processor.MarkedProcessor
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import org.slf4j.LoggerFactory
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir

import javax.tools.ToolProvider
import java.io.File
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
        Files.readString(SourceIndex.mappingFile(out, SourceKind.JAVA)).contains("example.Pair,example.Helper")

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
        !Files.readString(SourceIndex.mappingFile(out, SourceKind.JAVA)).contains("Pair.java")
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

    void "a change to a class whose constants are all private recompiles its dependents, not every source"() {
        given: "a constant only the class and its nested classes can read, which javac inlines nowhere else"
        write("example/Constants.java", "package example; public class Constants { private static final String VALUE = \"one\"; public String value() { return VALUE; } " +
            "public static class Inner { public String value() { return VALUE; } } }")
        write("example/Reader.java", "package example; public class Reader { public String read() { return new Constants().value(); } }")
        compiler.compile(request().asFull())

        expect:
        !ClassDependencyIndex.scan(out).declaresConstants("example.Constants")

        when:
        Path constants = write("example/Constants.java", "package example; public class Constants { private static final String VALUE = \"two\"; public String value() { return VALUE; } " +
            "public static class Inner { public String value() { return VALUE; } } }")
        def result = compiler.compile(request([constants] as Set, [] as Set))

        then: "the class and its dependent compiled, with the nested class that read the constant"
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString().toSet() == ["Constants.java", "Reader.java"] as Set
        loadAndCall("example.Reader", "read") == "two"
        loadAndCall('example.Constants$Inner', "value") == "two"
    }

    void "a constant another class can read counts, whatever its access"() {
        given:
        write("example/Package.java", "package example; public class Package { static final int VALUE = 1; }")
        write("example/Protected.java", "package example; public class Protected { protected static final int VALUE = 1; }")
        write("example/Nested.java", "package example; public class Nested { public static class Inner { public static final String VALUE = \"x\"; } }")
        write("example/Field.java", "package example; public class Field { private static final Object NOT_CONSTANT = new Object(); private final int instance = 1; }")
        write("example/Iface.java", "package example; public interface Iface { int VALUE = 1; }")
        compiler.compile(request().asFull())

        when:
        def index = ClassDependencyIndex.scan(out)

        then:
        index.declaresConstants("example.Package")
        index.declaresConstants("example.Protected")
        index.declaresConstants("example.Nested")
        index.declaresConstants("example.Iface")
        !index.declaresConstants("example.Field")
    }

    void "a member removed two classes up fails the compilation of a class that reached it through the subclass"() {
        given: "a caller whose class file names the subclass alone: javac qualifies an inherited member by the receiver's type"
        write("example/GrandParent.java", "package example; public class GrandParent { public String greet() { return \"hello\"; } }")
        write("example/Middle.java", "package example; public class Middle extends GrandParent { }")
        write("example/Caller.java", "package example; public class Caller { public String call() { return new Middle().greet(); } }")
        compiler.compile(request().asFull())

        expect: "the caller depends on the grandparent through the middle class only"
        !ClassDependencyIndex.scan(out).referencesOf("example.Caller").contains("example.GrandParent")
        ClassDependencyIndex.scan(out).referencesOf("example.Caller").contains("example.Middle")

        when:
        Path grandParent = write("example/GrandParent.java", "package example; public class GrandParent { }")
        def result = compiler.compile(request([grandParent] as Set, [] as Set))

        then: "the dependents of dependents compiled too, and the caller fails rather than linking against a method that went"
        result.status == CompilationResult.Status.FAILED
        result.errors()*.file()*.fileName*.toString() == ["Caller.java"]
    }

    void "a default method removed from an interface fails the compilation of a class that called it through a subinterface"() {
        given:
        write("example/First.java", "package example; public interface First { default String hello() { return \"hello\"; } }")
        write("example/Second.java", "package example; public interface Second extends First { }")
        write("example/User.java", "package example; public class User { public String use(Second second) { return second.hello(); } }")
        compiler.compile(request().asFull())

        expect:
        !ClassDependencyIndex.scan(out).referencesOf("example.User").contains("example.First")

        when:
        Path first = write("example/First.java", "package example; public interface First { }")
        def result = compiler.compile(request([first] as Set, [] as Set))

        then:
        result.status == CompilationResult.Status.FAILED
        result.errors()*.file()*.fileName*.toString() == ["User.java"]
    }

    void "a bean's metadata follows an annotation its stereotype is annotated with"() {
        given: "a bean annotated with a stereotype, itself annotated with another annotation of the project"
        write("example/Far.java", "package example; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Far { }")
        write("example/Outer.java", "package example; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Outer { }")
        write("example/Stereo.java", "package example; @Outer @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Stereo { }")
        write("example/Service.java", "package example; @Stereo @jakarta.inject.Singleton public class Service { }")
        compiler.compile(fullClasspathRequest().asFull())

        expect: "what was generated for the bean names every annotation of the hierarchy, so the bean depends on each"
        ClassDependencyIndex.scan(out).referencesOf("example.Service").contains("example.Outer")
        !definitionMentions("Service", "example.Far")

        when: "the annotation on the stereotype gains an annotation of its own"
        Path outer = write("example/Outer.java", "package example; @Far @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Outer { }")
        def result = compiler.compile(fullClasspathRequest([outer] as Set))

        then: "the bean's definition, two annotations away, holds it as a stereotype"
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString().contains("Service.java")
        definitionMentions("Service", "example.Far")
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
        !Files.exists(SourceIndex.mappingFile(out, SourceKind.JAVA))
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
        Files.readString(SourceIndex.mappingFile(out, SourceKind.JAVA)).contains("AloneGenerated")

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

    void "a class generated from several sources comes back when one of them compiles, repeatedly"() {
        given: "a bean nested in a holder, whose generated classes javac credits to the interface it extends too"
        write("example/Client.java", """package example;
            @io.micronaut.aop.Introduction @io.micronaut.context.annotation.Bean @jakarta.inject.Singleton
            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            public @interface Client { }""")
        Path repository = write("example/Repository.java", "package example; public interface Repository { String name(); }")
        write("example/holder/Repositories.java", "package example.holder; public final class Repositories { @example.Client public interface Nested extends example.Repository { } }")
        compiler.compile(fullClasspathRequest().asFull())

        expect: "the interface's source is credited with the holder"
        Files.readString(SourceIndex.mappingFile(out, SourceKind.JAVA)).contains("example.holder.Repositories")

        when: "the interface is edited, twice"
        def results = (1..2).collect {
            write("example/Repository.java", "package example; public interface Repository { String name(); /* edit $it */ }")
            compiler.compile(fullClasspathRequest([repository] as Set))
        }

        then: "each time the holder compiled again, so that what it generated is still there"
        results*.status.every { it == CompilationResult.Status.SUCCESS }
        results.every { it.compiledSources*.fileName*.toString().containsAll(["Repository.java", "Repositories.java"]) }
        Files.exists(out.resolve("example/holder/Repositories.class"))
        Files.exists(out.resolve('example/holder/$Repositories$Nested$Intercepted$Definition.class'))
        Files.exists(out.resolve('META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/example.holder.$Repositories$Nested$Intercepted$Definition'))
    }

    void "a bean inheriting injection points keeps its generated classes when its superclass compiles"() {
        given: "a bean whose definition javac credits to the source of its superclass too"
        write("example/Parent.java", "package example; public abstract class Parent { @jakarta.inject.Inject public Helper helper; }")
        write("example/Helper.java", "package example; @jakarta.inject.Singleton public class Helper { }")
        write("example/Child.java", "package example; @jakarta.inject.Singleton public class Child extends Parent { }")
        compiler.compile(fullClasspathRequest().asFull())

        when: "the superclass is edited"
        Path parent = write("example/Parent.java", "package example; public abstract class Parent { @jakarta.inject.Inject public Helper helper; /* edit */ }")
        def result = compiler.compile(fullClasspathRequest([parent] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString().containsAll(["Parent.java", "Child.java"])
        Files.exists(out.resolve("example/Child.class"))
        Files.exists(out.resolve('example/$Child$Definition.class'))
    }

    void "the subclasses of a base class with injection points compile together, and the size of the group is logged"() {
        given: "beans that inherit an injection point from a project base class, as controllers sharing one do"
        write("example/Controllers.java", "package example; public abstract class Controllers { @jakarta.inject.Inject public Service service; }")
        write("example/Service.java", "package example; @jakarta.inject.Singleton public class Service { }")
        Path first = write("example/First.java", "package example; @jakarta.inject.Singleton public class First extends Controllers { }")
        write("example/Second.java", "package example; @jakarta.inject.Singleton public class Second extends Controllers { }")
        write("example/Third.java", "package example; @jakarta.inject.Singleton public class Third extends Controllers { }")
        compiler.compile(fullClasspathRequest().asFull())
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(StagedSourceCompiler)
        def level = logger.level
        def logged = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        logged.start()
        logger.addAppender(logged)
        logger.level = ch.qos.logback.classic.Level.DEBUG

        when: "one of them is edited"
        write("example/First.java", "package example; @jakarta.inject.Singleton public class First extends Controllers { /* edit */ }")
        def result = compiler.compile(fullClasspathRequest([first] as Set))

        then: "every one credited with the base class compiles with it, and the debug log tells how many"
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString().toSet() == ["First.java", "Controllers.java", "Second.java", "Third.java"] as Set
        logged.list*.formattedMessage.any { it.contains("share an output with the 1 selected and 0 deleted: a group of 4 source(s) compiles together") }

        cleanup:
        logger.detachAppender(logged)
        logger.level = level
    }

    void "what a processor aggregates from several sources is written from all of them when one is edited, and without one that is deleted"() {
        given: "two sources a processor writes one source and one resource for"
        Path first = write("example/First.java", "package example; @io.micronaut.dev.compile.processor.Aggregated public class First { }")
        Path second = write("example/Second.java", "package example; @io.micronaut.dev.compile.processor.Aggregated public class Second { }")
        compiler.compile(request([] as Set, [] as Set, processorPath()).asFull())

        expect:
        Files.readString(out.resolve("META-INF/aggregated")) == "[First, Second]"

        when: "one of them is edited, twice"
        def results = (1..2).collect {
            write("example/First.java", "package example; @io.micronaut.dev.compile.processor.Aggregated public class First { /* edit $it */ }")
            compiler.compile(request([first] as Set, [] as Set, processorPath()))
        }

        then: "the other compiled too, so that nothing it contributed is lost"
        results*.status.every { it == CompilationResult.Status.SUCCESS }
        results.every { it.compiledSources*.fileName*.toString().containsAll(["First.java", "Second.java"]) }
        !results.any { it.compiledSources*.fileName*.toString().contains("Alone.java") }
        Files.readString(out.resolve("META-INF/aggregated")) == "[First, Second]"
        Files.readString(generated.resolve("generated/AggregateOf.java")).contains("[First, Second]")
        Files.exists(out.resolve("generated/AggregateOf.class"))

        when: "one of them is deleted"
        Files.delete(second)
        def afterDelete = compiler.compile(request([] as Set, [second] as Set, processorPath()))

        then: "what is aggregated is written again from the one left"
        afterDelete.status == CompilationResult.Status.SUCCESS
        afterDelete.compiledSources*.fileName*.toString() == ["First.java"]
        !Files.exists(out.resolve("example/Second.class"))
        Files.readString(out.resolve("META-INF/aggregated")) == "[First]"
        Files.readString(generated.resolve("generated/AggregateOf.java")).contains("[First]")
        Files.exists(out.resolve("generated/AggregateOf.class"))

        when: "the deleted one is restored, which shares no recorded output yet, and the other is edited"
        write("example/Second.java", "package example; @io.micronaut.dev.compile.processor.Aggregated public class Second { }")
        def afterRestore = compiler.compile(request([second] as Set, [] as Set, processorPath()))
        def afterEdit = compiler.compile(request([first] as Set, [] as Set, processorPath()))

        then: "what is aggregated has both again once one of them compiles after the restore"
        afterRestore.status == CompilationResult.Status.SUCCESS
        Files.exists(out.resolve("example/Second.class"))
        afterEdit.compiledSources*.fileName*.toString().containsAll(["First.java", "Second.java"])
        Files.readString(out.resolve("META-INF/aggregated")) == "[First, Second]"
    }

    void "a source credited with no shared output compiles alone"() {
        given: "generated classes, none of them credited to a second source"
        write("example/Helper.java", "package example; @jakarta.inject.Singleton public class Helper { }")
        Path own = write("example/Own.java", "package example; @jakarta.inject.Singleton public class Own { }")
        compiler.compile(fullClasspathRequest().asFull())

        when:
        def result = compiler.compile(fullClasspathRequest([own] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString() == ["Own.java"]
    }

    // Windows has a loader for each compilation: a jar a kept loader holds open could not be replaced
    @IgnoreIf({ os.windows })
    void "the processors' class loader is kept between compilations, and what their classes hold with it"() {
        given:
        write("example/Alone.java", "package example; @io.micronaut.dev.compile.processor.Counted public class Alone { public int value() { return 1; } }")

        when:
        def first = compiler.compile(request([] as Set, [] as Set, processorPath()).asFull())
        Path alone = write("example/Alone.java", "package example; @io.micronaut.dev.compile.processor.Counted public class Alone { public int value() { return 2; } }")
        def second = compiler.compile(request([alone] as Set, [] as Set, processorPath()))

        then: "the processor's class was initialized once and its instances twice, in one loader"
        first.status == CompilationResult.Status.SUCCESS
        second.status == CompilationResult.Status.SUCCESS
        Files.readString(out.resolve("META-INF/counted/Alone")) == "2"

        when: "another compiler compiles the same sources"
        def other = new JavacSourceCompiler().compile(request([] as Set, [] as Set, processorPath()).asFull())

        then: "it has a loader of its own"
        other.status == CompilationResult.Status.SUCCESS
        Files.readString(out.resolve("META-INF/counted/Alone")) == "1"

        cleanup:
        compiler.close()
    }

    @IgnoreIf({ os.windows })
    void "the processors load again when their path or what it holds changes, and after one of them failed"() {
        given:
        Path extra = Files.createDirectories(project.resolve("extra-processors"))
        Path alone = write("example/Alone.java", "package example; @io.micronaut.dev.compile.processor.Counted public class Alone { }")
        compiler.compile(request([] as Set, [] as Set, processorPath()).asFull())

        when: "the path gains an entry"
        compiler.compile(request([alone] as Set, [] as Set, processorPath() + [extra]))

        then:
        Files.readString(out.resolve("META-INF/counted/Alone")) == "1"

        when: "a directory of the path gains a file"
        Files.writeString(extra.resolve("Added.class"), "x")
        compiler.compile(request([alone] as Set, [] as Set, processorPath() + [extra]))

        then:
        Files.readString(out.resolve("META-INF/counted/Alone")) == "1"

        when: "nothing changes"
        compiler.compile(request([alone] as Set, [] as Set, processorPath() + [extra]))

        then:
        Files.readString(out.resolve("META-INF/counted/Alone")) == "2"

        when: "a file of the path is rewritten to the same size, and its time set back"
        def time = Files.getLastModifiedTime(extra.resolve("Added.class"))
        Files.writeString(extra.resolve("Added.class"), "y")
        Files.setLastModifiedTime(extra.resolve("Added.class"), time)
        compiler.compile(request([alone] as Set, [] as Set, processorPath() + [extra]))

        then:
        Files.readString(out.resolve("META-INF/counted/Alone")) == "1"

        when: "a directory that a jar of the path names in its manifest gains a file"
        Path manifested = Files.createDirectories(project.resolve("manifested"))
        Path jar = project.resolve("with-class-path.jar")
        def manifest = new java.util.jar.Manifest()
        manifest.mainAttributes.put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0")
        manifest.mainAttributes.put(java.util.jar.Attributes.Name.CLASS_PATH, "manifested/")
        new java.util.jar.JarOutputStream(Files.newOutputStream(jar), manifest).close()
        compiler.compile(request([alone] as Set, [] as Set, processorPath() + [extra, jar]))
        compiler.compile(request([alone] as Set, [] as Set, processorPath() + [extra, jar]))
        String kept = Files.readString(out.resolve("META-INF/counted/Alone"))
        Files.writeString(manifested.resolve("Added.class"), "x")
        compiler.compile(request([alone] as Set, [] as Set, processorPath() + [extra, jar]))

        then:
        kept == "2"
        Files.readString(out.resolve("META-INF/counted/Alone")) == "1"

        when: "a processor fails with an exception, rather than the sources failing to compile"
        write("example/Alone.java", "package example; @io.micronaut.dev.compile.processor.Counted(fail = true) public class Alone { }")
        compiler.compile(request([alone] as Set, [] as Set, processorPath() + [extra]))

        then:
        thrown(RuntimeException)

        when: "the next compilation"
        write("example/Alone.java", "package example; @io.micronaut.dev.compile.processor.Counted public class Alone { }")
        def result = compiler.compile(request([alone] as Set, [] as Set, processorPath() + [extra]))

        then: "the processors start over: what the failure left in them is gone"
        result.status == CompilationResult.Status.SUCCESS
        Files.readString(out.resolve("META-INF/counted/Alone")) == "1"

        when: "a source fails to compile, after the processors ran"
        write("example/Alone.java", "package example; @io.micronaut.dev.compile.processor.Counted public class Alone { int value = \"text\"; }")
        def failed = compiler.compile(request([alone] as Set, [] as Set, processorPath() + [extra]))
        write("example/Alone.java", "package example; @io.micronaut.dev.compile.processor.Counted public class Alone { }")
        result = compiler.compile(request([alone] as Set, [] as Set, processorPath() + [extra]))

        then: "the processors start over too: one of them may have failed and been skipped, or kept what the final round releases"
        failed.status == CompilationResult.Status.FAILED
        result.status == CompilationResult.Status.SUCCESS
        Files.readString(out.resolve("META-INF/counted/Alone")) == "1"

        cleanup:
        compiler.close()
    }

    @IgnoreIf({ os.windows })
    void "the processors' class loader is not kept with the system property set to false"() {
        given:
        Path alone = write("example/Alone.java", "package example; @io.micronaut.dev.compile.processor.Counted public class Alone { }")
        System.setProperty(ProcessorLoaders.RETAIN_PROPERTY, "false")

        when:
        compiler.compile(request([] as Set, [] as Set, processorPath()).asFull())
        def second = compiler.compile(request([alone] as Set, [] as Set, processorPath()))

        then: "each compilation loads the processors again"
        second.status == CompilationResult.Status.SUCCESS
        Files.readString(out.resolve("META-INF/counted/Alone")) == "1"

        cleanup:
        System.clearProperty(ProcessorLoaders.RETAIN_PROPERTY)
        compiler.close()
    }

    @IgnoreIf({ os.windows })
    void "a loader that is no longer kept closes only once no compilation uses it"() {
        given:
        ProcessorLoaders loaders = new ProcessorLoaders()
        ClassLoader first = loaders.loaderOf(processorPath())
        ClassLoader second = loaders.loaderOf(processorPath())

        expect: "one loader for both compilations"
        first.is(second)

        when: "it is discarded while both use it"
        loaders.discard(processorPath())
        loaders.returned(first)

        then:
        !ProcessorLoaders.isClosed(first)
        first.loadClass("io.micronaut.dev.compile.processor.CountedProcessor") != null

        when: "the last compilation returns it"
        loaders.returned(second)

        then:
        ProcessorLoaders.isClosed(first)

        when: "the compiler closes while a compilation uses the next one"
        ClassLoader next = loaders.loaderOf(processorPath())
        loaders.close()

        then:
        !next.is(first)
        !ProcessorLoaders.isClosed(next)

        when:
        loaders.returned(next)

        then:
        ProcessorLoaders.isClosed(next)
    }

    void "an evaluated expression keeps the name of its class when its bean compiles again with the same processors"() {
        given:
        write("example/Expressive.java", "package example; @jakarta.inject.Singleton public class Expressive { @io.micronaut.context.annotation.Value(\"#{ 1 + 1 }\") public int two; public int value() { return 1; } }")
        compiler.compile(fullClasspathRequest().asFull())

        expect:
        Files.exists(out.resolve('example/$Expressive$Expr0.class'))

        when:
        Path expressive = write("example/Expressive.java", "package example; @jakarta.inject.Singleton public class Expressive { @io.micronaut.context.annotation.Value(\"#{ 1 + 1 }\") public int two; public int value() { return 2; } }")
        def result = compiler.compile(fullClasspathRequest([expressive] as Set))

        then: "as a compilation in a loader of its own names it: a new name is a class added and one removed, which no redefinition can apply"
        result.status == CompilationResult.Status.SUCCESS
        Files.exists(out.resolve('example/$Expressive$Expr0.class'))
        !Files.exists(out.resolve('example/$Expressive$Expr1.class'))

        cleanup:
        compiler.close()
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

    void "a system property for the JVM of a forked compiler reaches the processors for the compilation only, in place of a flag javac refuses"() {
        given: "the options of a build that forks javac, with a property set, and another absent"
        System.setProperty("marked.previous", "before")
        write("example/Alone.java", "package example; @io.micronaut.dev.compile.processor.Marked public class Alone { }")
        def request = new CompilationRequest(SourceKind.JAVA, [new SourceRoot(SourceKind.JAVA, src)], [] as Set, [] as Set, false, processorPath(), processorPath(), out, generated,
            ["-parameters", "-J-Dmarked.suffix=-forked", "-J-Dmarked.previous=compiling"])

        when:
        def result = compiler.compile(request.asFull())

        then: "the processor read it, as it does in a forked compiler"
        result.status == CompilationResult.Status.SUCCESS
        Files.readString(out.resolve("META-INF/marked/Alone")) == "Alone-forked"

        and: "the running JVM has what it had"
        System.getProperty("marked.suffix") == null
        System.getProperty("marked.previous") == "before"

        when: "the compilation fails"
        write("example/Alone.java", "package example; @io.micronaut.dev.compile.processor.Marked public class Alone { broken }")
        result = compiler.compile(request.asFull())

        then: "the properties are restored all the same"
        result.status == CompilationResult.Status.FAILED
        System.getProperty("marked.suffix") == null
        System.getProperty("marked.previous") == "before"

        cleanup:
        System.clearProperty("marked.previous")
    }

    void "a JVM option of a forked compiler that cannot apply is dropped with one warning that names it"() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger(JavacSourceCompiler)
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        logger.addAppender(appender)
        def request = new CompilationRequest(SourceKind.JAVA, [new SourceRoot(SourceKind.JAVA, src)], [] as Set, [] as Set, false, [], [], out, generated, ["-parameters", "-J-Xmx64m", "-J-Dok=1"])

        when: "two compilations"
        def results = (1..2).collect { compiler.compile(request.asFull()) }

        then:
        results*.status.every { it == CompilationResult.Status.SUCCESS }
        appender.list.size() == 1
        appender.list[0].level == Level.WARN
        appender.list[0].formattedMessage.contains("-J-Xmx64m")
        !appender.list[0].formattedMessage.contains("-J-Dok=1")

        cleanup:
        logger.detachAppender(appender)
    }

    void "the options of a forked compiler's JVM are told apart from javac's"() {
        when:
        def options = JavacJvmOptions.of(["-parameters", "-J-Da=1=2", "-J-Db", "-J-Xss1m", "-J", "-J-D", "-J-D=bad", "--release", "25"])

        then:
        options.compilerOptions() == ["-parameters", "--release", "25"]
        options.systemProperties() == [a: "1=2", b: ""]
        options.ignored() == ["-J-Xss1m", "-J", "-J-D", "-J-D=bad"]
    }

    void "a module option of a forked compiler's JVM is checked against the running JVM, in either form"() {
        given:
        def inEffect = ["--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"] as Set

        when:
        def options = JavacJvmOptions.of([
            "-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "-J--add-exports", "-Jjdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "-J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
            "-parameters",
            "-J--add-exportsfoo",
            "-J--add-opens"
        ], { null }, inEffect::contains)

        then: "what the running JVM has is dropped quietly, what it lacks is named as a JVM argument"
        options.compilerOptions() == ["-parameters"]
        options.missingModuleOptions() == [
            "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED"
        ]
        options.ignored() == ["-J--add-exportsfoo", "-J--add-opens"]
        options.systemProperties().isEmpty()
    }

    void "the running JVM's module layer tells whether a module option is in effect"() {
        expect:
        JavacJvmOptions.inEffect("--add-exports=java.base/java.util=ALL-UNNAMED")
        JavacJvmOptions.inEffect("--add-exports=java.base/java.util=ALL-UNNAMED,java.logging")
        !JavacJvmOptions.inEffect("--add-exports=no.such.module/a.b=ALL-UNNAMED")
        !JavacJvmOptions.inEffect("--add-exports=java.base/java.util=no.such.module")
        !JavacJvmOptions.inEffect("--add-exports=java.base")
    }

    void "a property the JDK reads only as it starts is not set for the compilation"() {
        when:
        def options = JavacJvmOptions.of(["-J-Dfile.encoding=ISO-8859-1", "-J-Duser.language=fr", "-J-Duser.language=en", "-J-Dother=1"],
            { it == "user.language" ? "en" : "UTF-8" }, { true })

        then: "a value other than the running JVM's is named, the same one is dropped quietly, the last one counting as in a JVM"
        options.systemProperties() == [other: "1"]
        options.startupProperties() == ["-J-Dfile.encoding=ISO-8859-1"]
        options.ignored().isEmpty()
    }

    void "the options the application's JVM cannot honour are named once, each with what to do"() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger(JavacSourceCompiler)
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        logger.addAppender(appender)
        String encoding = System.getProperty("file.encoding")
        String other = encoding == "ISO-8859-1" ? "UTF-16" : "ISO-8859-1"
        def request = new CompilationRequest(SourceKind.JAVA, [new SourceRoot(SourceKind.JAVA, src)], [] as Set, [] as Set, false, [], [], out, generated,
            ["-J-Dfile.encoding=" + other, "-J--add-exports=no.such.module/a.b=ALL-UNNAMED"])

        when: "two compilations"
        def results = (1..2).collect { compiler.compile(request.asFull()) }

        then:
        results*.status.every { it == CompilationResult.Status.SUCCESS }
        appender.list.size() == 2
        appender.list.every { it.level == Level.WARN }
        appender.list.any { it.formattedMessage.contains("-J-Dfile.encoding=" + other) && it.formattedMessage.contains("-encoding") }
        appender.list.any { it.formattedMessage.contains("--add-exports=no.such.module/a.b=ALL-UNNAMED") && it.formattedMessage.contains("IllegalAccessError") }

        and: "the application's property is left alone"
        System.getProperty("file.encoding") == encoding

        cleanup:
        logger.detachAppender(appender)
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

    void "the processors load with the processor path alone, so a package they share with the launch class path is not split"() {
        given: "a processor in micronaut-inject's package that uses one of its package-private classes, on a processor path"
        Path processor = compileSplitPackageProcessor()
        write("example/Alone.java", "package example; public class Alone { }")

        when: "micronaut-inject is on the processor path as a separate copy, and on the launch class path as well, as a build plugin launches a project"
        def result = compiler.compile(request([] as Set, [] as Set, [processor, copyOf(INJECT), copyOf(CORE)]).asFull())

        then: "the processor and its package's classes come from the processor path, so the package-private class is accessible"
        result.status == CompilationResult.Status.SUCCESS
        Files.readString(out.resolve("META-INF/split-package")) == "io.micronaut.inject.annotation.EnvironmentOptionalValuesMap"
    }

    /**
     * Whether a class Micronaut generated for a bean names a class: its annotation metadata names the annotations.
     */
    private boolean definitionMentions(String bean, String className) {
        Files.list(out.resolve("example")).withCloseable { files ->
            files.filter { it.fileName.toString().startsWith('$' + bean + '$') }
                .anyMatch { new String(Files.readAllBytes(it), java.nio.charset.StandardCharsets.ISO_8859_1).contains(className) }
        }
    }

    /**
     * A request with Micronaut's own processors, from the test class path, as an application compiles.
     */
    private CompilationRequest fullClasspathRequest(Set<Path> changed = [] as Set) {
        List<Path> classpath = System.getProperty("java.class.path").split(File.pathSeparator).collect { Path.of(it) }
        new CompilationRequest(SourceKind.JAVA, [new SourceRoot(SourceKind.JAVA, src)], changed, [] as Set, false, classpath, classpath, out, generated, ["-parameters"])
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

    private static final Path INJECT = Path.of(AnnotationMetadataHierarchy.protectionDomain.codeSource.location.toURI())
    private static final Path CORE = Path.of(OptionalValues.protectionDomain.codeSource.location.toURI())

    /**
     * A copy of a launch class path entry, so that the processor path differs from the launch class path.
     */
    private Path copyOf(Path entry) {
        Path copy = project.resolve("processor-path").resolve(entry.fileName.toString() + "-" + entry.hashCode())
        if (Files.isDirectory(entry)) {
            Files.walk(entry).withCloseable { paths ->
                paths.forEach { Path path ->
                    Path target = copy.resolve(entry.relativize(path).toString())
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target)
                    } else {
                        Files.copy(path, target)
                    }
                }
            }
        } else {
            Files.createDirectories(copy.parent)
            Files.copy(entry, copy)
        }
        copy
    }

    /**
     * Compiles a processor declared in micronaut-inject's package, which reads a package-private class of it, with
     * its service file, into a directory of its own: it is not on the launch class path.
     */
    private Path compileSplitPackageProcessor() {
        Path sources = Files.createDirectories(project.resolve("split/src/io/micronaut/inject/annotation"))
        Path classes = Files.createDirectories(project.resolve("split/classes"))
        Path source = sources.resolve("SplitPackageProcessor.java")
        Files.writeString(source, """
            package io.micronaut.inject.annotation;

            import java.io.IOException;
            import java.io.UncheckedIOException;
            import java.io.Writer;
            import java.util.Set;
            import javax.annotation.processing.AbstractProcessor;
            import javax.annotation.processing.RoundEnvironment;
            import javax.annotation.processing.SupportedAnnotationTypes;
            import javax.lang.model.SourceVersion;
            import javax.lang.model.element.TypeElement;
            import javax.tools.StandardLocation;

            @SupportedAnnotationTypes("*")
            public class SplitPackageProcessor extends AbstractProcessor {
                private boolean written;

                @Override
                public SourceVersion getSupportedSourceVersion() {
                    return SourceVersion.latestSupported();
                }

                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
                    if (!written) {
                        written = true;
                        try (Writer writer = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/split-package").openWriter()) {
                            writer.write(EnvironmentOptionalValuesMap.class.getName());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                    return false;
                }
            }
            """.stripIndent())
        int status = ToolProvider.systemJavaCompiler.run(null, null, null, "-proc:none",
            "-cp", [INJECT, CORE].join(File.pathSeparator), "-d", classes.toString(), source.toString())
        assert status == 0
        Path services = Files.createDirectories(classes.resolve("META-INF/services"))
        Files.writeString(services.resolve("javax.annotation.processing.Processor"), "io.micronaut.inject.annotation.SplitPackageProcessor")
        classes
    }
}
