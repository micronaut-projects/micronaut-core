package io.micronaut.dev.compile

import io.micronaut.dev.compile.processor.MarkedProcessor
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

class SiblingOutputSpec extends Specification {

    @TempDir
    Path project

    Path appSrc
    Path libSrc
    Path appOut
    Path appGenerated
    SiblingOutput siblings
    JavacSourceCompiler javac = new JavacSourceCompiler()

    def setup() {
        appSrc = Files.createDirectories(project.resolve("app/src"))
        libSrc = Files.createDirectories(project.resolve("lib/src"))
        appOut = project.resolve("app/build/classes")
        appGenerated = project.resolve("app/build/generated")
        siblings = new SiblingOutput(project.resolve("app/build/micronaut-dev/siblings"), [libSrc])
    }

    def cleanup() {
        siblings.release()
    }

    void "everything the compilation of a sibling's source produces goes to the sibling output: classes, generated sources and resources"() {
        given:
        write(libSrc, "lib/Lib.java", '''package lib;
            @io.micronaut.dev.compile.processor.Marked
            public class Lib { public static class Nested { } public String name() { return "one"; } }
            class Extra { }''')
        write(appSrc, "app/Main.java", '''package app;
            @io.micronaut.dev.compile.processor.Marked
            public class Main { public String name() { return new lib.Lib().name(); } }''')

        when: "both are compiled in one run"
        def result = javac.compile(request(processorPath()).asFull())

        then: "each output goes with its source"
        result.status == CompilationResult.Status.SUCCESS
        files(appOut) == ["META-INF/marked/Main", "app/Main.class", "app/MainGenerated.class"] as Set
        files(appGenerated) == ["app/MainGenerated.java"] as Set
        files(siblings.classes()) == ["META-INF/marked/Lib", "lib/Extra.class", 'lib/Lib$Nested.class', "lib/Lib.class", "lib/LibGenerated.class"] as Set
        files(siblings.generatedSources(SourceKind.JAVA)) == ["lib/LibGenerated.java"] as Set

        when: "the sibling's source changes, and what depends on it compiles with it"
        Path lib = write(libSrc, "lib/Lib.java", '''package lib;
            @io.micronaut.dev.compile.processor.Marked
            public class Lib { public String name() { return "two"; } }''')
        result = javac.compile(request(processorPath(), [lib] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString().toSet() == ["Lib.java", "Main.java"] as Set
        files(appOut) == ["META-INF/marked/Main", "app/Main.class", "app/MainGenerated.class"] as Set
        files(siblings.classes()) == ["META-INF/marked/Lib", "lib/Lib.class", "lib/LibGenerated.class"] as Set
    }

    void "the classes of a sibling's source compiled only because the application's source references it go to the sibling output"() {
        given: "the sibling's build output is not on the compile classpath, as in a Maven reactor"
        write(libSrc, "lib/Lib.java", 'package lib; public class Lib { }')
        Path main = write(appSrc, "app/Main.java", 'package app; public class Main { lib.Lib lib; }')

        when:
        def result = javac.compile(request([], [main] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        files(appOut) == ["app/Main.class"] as Set
        files(siblings.classes()) == ["lib/Lib.class"] as Set
    }

    void "a sibling's class in the sibling output wins over its build output on the compile classpath, and over the source it was compiled from"() {
        given: "the sibling's build output holds the class without the method"
        Path libBuild = Files.createDirectories(project.resolve("lib/build/classes"))
        Path lib = write(libSrc, "lib/Lib.java", 'package lib; public class Lib { }')
        assert javac.compile(new CompilationRequest(SourceKind.JAVA, [new SourceRoot(SourceKind.JAVA, libSrc)], [] as Set, [] as Set, true, [], [], libBuild, project.resolve("lib/build/generated"), [])).success

        when: "an edit adds the method"
        write(libSrc, "lib/Lib.java", 'package lib; public class Lib { public static int added() { return 1; } }')
        assert javac.compile(request([libBuild], [lib] as Set)).success
        and: "an application source that uses it compiles alone, with the sibling's build output first on its classpath"
        Path main = write(appSrc, "app/Main.java", 'package app; public class Main { int value = lib.Lib.added(); }')
        def result = javac.compile(request([libBuild], [main] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString() == ["Main.java"]
        files(appOut) == ["app/Main.class"] as Set
    }

    void "the dependency index reads the sibling output, so what depends on a sibling's class in it compiles again"() {
        given:
        Path base = write(libSrc, "lib/Base.java", 'package lib; public class Base { }')
        write(libSrc, "lib/Derived.java", 'package lib; public class Derived extends Base { }')
        assert javac.compile(request([]).asFull()).success

        when:
        write(libSrc, "lib/Base.java", 'package lib; public class Base { int changed; }')
        def result = javac.compile(request([], [base] as Set))

        then:
        result.compiledSources*.fileName*.toString().toSet() == ["Base.java", "Derived.java"] as Set
    }

    void "a sibling's deleted source takes everything it produced from the sibling output, and a failed compilation changes nothing"() {
        given:
        Path lib = write(libSrc, "lib/Lib.java", 'package lib; @io.micronaut.dev.compile.processor.Marked public class Lib { public static class Nested { } }')
        write(libSrc, "lib/Other.java", 'package lib; public class Other { }')
        assert javac.compile(request(processorPath()).asFull()).success
        Set<String> whole = files(siblings.classes())

        when: "an edit that does not compile"
        Path other = write(libSrc, "lib/Other.java", 'package lib; public class Other { nonsense }')
        def failed = javac.compile(request(processorPath(), [other] as Set))

        then:
        !failed.success
        files(siblings.classes()) == whole
        !Files.exists(StagedSourceCompiler.staging(siblings.classes()))

        when:
        write(libSrc, "lib/Other.java", 'package lib; public class Other { }')
        Files.delete(lib)
        def result = javac.compile(request(processorPath(), [] as Set, [lib] as Set))

        then:
        result.status == CompilationResult.Status.NOTHING_TO_DO
        files(siblings.classes()) == ["lib/Other.class"] as Set
        files(siblings.generatedSources(SourceKind.JAVA)).isEmpty()
        files(appOut).isEmpty()
    }

    void "a file no source is credited with goes to the sibling output when a sibling compiled, and to the application's when none did, without leaving a copy that shadows it"() {
        given:
        Path main = write(appSrc, "app/Main.java", 'package app; @io.micronaut.dev.compile.processor.Uncredited public class Main { }')
        write(libSrc, "lib/Lib.java", 'package lib; @io.micronaut.dev.compile.processor.Uncredited public class Lib { }')

        when: "one run compiles the sources of both"
        assert javac.compile(request(processorPath()).asFull()).success

        then:
        files(appOut) == ["app/Main.class"] as Set
        files(siblings.classes()) == ["META-INF/uncredited/index", "lib/Lib.class"] as Set

        when: "then the application's source alone"
        write(appSrc, "app/Main.java", 'package app; @io.micronaut.dev.compile.processor.Uncredited public class Main { int changed; }')
        assert javac.compile(request(processorPath(), [main] as Set)).success

        then:
        files(appOut) == ["META-INF/uncredited/index", "app/Main.class"] as Set
        files(siblings.classes()) == ["lib/Lib.class"] as Set
    }

    void "the sibling output is emptied for a new session, whatever ended the last one"() {
        given:
        siblings.prepare()
        leaveClasses()

        when: "the process that held it is gone, as after a kill"
        siblings.release()
        siblings.prepare()

        then:
        files(siblings.directory()).isEmpty()
        Files.isDirectory(siblings.classes())
    }

    void "a launcher started again in the same process continues its session"() {
        given:
        siblings.prepare()
        leaveClasses()

        when:
        siblings.prepare()

        then:
        files(siblings.classes()) == ["lib/Lib.class"] as Set
    }

    void "the sibling output is kept while another process uses it"() {
        given: "another development process of the project, holding its share"
        Files.createDirectories(siblings.directory())
        leaveClasses()
        Path holderClasses = Path.of(SharedLockHolder.protectionDomain.codeSource.location.toURI())
        Process other = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", holderClasses.toString(),
            SharedLockHolder.name, siblings.directory().resolveSibling("siblings.lock").toString()).redirectErrorStream(true).start()
        BufferedReader output = new BufferedReader(new InputStreamReader(other.inputStream))
        assert output.readLine() == "held"

        when:
        siblings.prepare()

        then: "it is the other process's session that continues"
        files(siblings.classes()) == ["lib/Lib.class"] as Set

        cleanup:
        other?.outputStream?.close()
        if (other != null && !other.waitFor(30, TimeUnit.SECONDS)) {
            other.destroyForcibly()
        }
    }

    void "the sibling output is kept while another runtime of this JVM, loaded apart, uses it"() {
        given: "another holder of the lock in this JVM"
        Files.createDirectories(siblings.directory())
        leaveClasses()
        FileChannel other = FileChannel.open(siblings.directory().resolveSibling("siblings.lock"), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)
        def held = other.lock(0, Long.MAX_VALUE, true)

        when:
        siblings.prepare()

        then:
        files(siblings.classes()) == ["lib/Lib.class"] as Set

        cleanup:
        held?.release()
        other?.close()
    }

    void "a relaunch keeps what the process before it compiled, once, and a mark left long ago does not"() {
        given:
        siblings.prepare()
        leaveClasses()
        siblings.markRelaunch()
        siblings.release()

        when: "the process relaunched"
        siblings.prepare()

        then:
        files(siblings.classes()) == ["lib/Lib.class"] as Set

        when: "the next session"
        siblings.release()
        siblings.prepare()

        then:
        files(siblings.classes()).isEmpty()

        when: "a mark of a build tool stopped before it relaunched, found by a session started later"
        leaveClasses()
        siblings.markRelaunch()
        siblings.release()
        Files.setLastModifiedTime(siblings.directory().resolveSibling("siblings.relaunch"), FileTime.from(Instant.now().minusSeconds(600)))
        siblings.prepare()

        then:
        files(siblings.classes()).isEmpty()
        !Files.exists(siblings.directory().resolveSibling("siblings.relaunch"))
    }

    void "a Groovy sibling's classes and bean definitions go to the sibling output"() {
        given:
        GroovySourceCompiler groovy = new GroovySourceCompiler()
        Path lib = write(libSrc, "lib/Service.groovy", "package lib\n@jakarta.inject.Singleton\nclass Service { String hello() { 'one' } }")
        write(appSrc, "app/Main.groovy", "package app\nclass Main { String hello() { new lib.Service().hello() } }")
        List<Path> classpath = testClasspath()
        CompilationRequest request = new CompilationRequest(SourceKind.GROOVY, roots(SourceKind.GROOVY), [] as Set, [] as Set, false, classpath, [], appOut, appGenerated, []).withSiblingOutput(siblings)

        when:
        def result = groovy.compile(request.asFull())

        then:
        result.status == CompilationResult.Status.SUCCESS
        files(appOut) == ["app/Main.class"] as Set
        files(siblings.classes()).containsAll(["lib/Service.class", 'lib/$Service$Definition.class'])
        files(siblings.classes()).any { it.startsWith("META-INF/micronaut/") && it.contains('lib.$Service$Definition') }

        when: "the sibling's source goes"
        Files.delete(lib)
        write(appSrc, "app/Main.groovy", "package app\nclass Main { }")
        result = groovy.compile(new CompilationRequest(SourceKind.GROOVY, roots(SourceKind.GROOVY), [appSrc.resolve("app/Main.groovy")] as Set, [lib] as Set, false, classpath, [], appOut, appGenerated, []).withSiblingOutput(siblings))

        then:
        result.status == CompilationResult.Status.SUCCESS
        files(siblings.classes()).isEmpty()
        files(appOut) == ["app/Main.class"] as Set
    }

    void "a Groovy application source compiles against what javac compiled of a sibling into the sibling output"() {
        given: "the sibling's build output holds the class without the method"
        Path libBuild = Files.createDirectories(project.resolve("lib/build/classes"))
        Path lib = write(libSrc, "lib/Lib.java", 'package lib; public class Lib { }')
        assert javac.compile(new CompilationRequest(SourceKind.JAVA, [new SourceRoot(SourceKind.JAVA, libSrc)], [] as Set, [] as Set, true, [], [], libBuild, project.resolve("lib/build/generated"), [])).success
        write(libSrc, "lib/Lib.java", 'package lib; public class Lib { public static int added() { return 1; } }')
        assert javac.compile(request([libBuild], [lib] as Set)).success
        Path main = write(appSrc, "app/Main.groovy", "package app\n@groovy.transform.CompileStatic\nclass Main { int value = lib.Lib.added() }")
        List<Path> classpath = [libBuild] + testClasspath()

        when:
        def result = new GroovySourceCompiler().compile(new CompilationRequest(SourceKind.GROOVY, [new SourceRoot(SourceKind.GROOVY, appSrc)], [main] as Set, [] as Set, false, classpath, [], appOut, appGenerated, []).withSiblingOutput(siblings))

        then:
        result.status == CompilationResult.Status.SUCCESS
        files(appOut) == ["app/Main.class"] as Set
    }

    void "a Kotlin sibling's classes go to the sibling output, and each output's module metadata lists the top-level declarations it holds"() {
        given:
        KotlinSourceCompiler kotlin = new KotlinSourceCompiler()
        Path lib = write(libSrc, "lib/Helper.kt", "package lib\nfun helper(): String = \"one\"\n@jakarta.inject.Singleton\nclass Service")
        Path main = write(appSrc, "app/Main.kt", "package app\nfun main2(): String = lib.helper()")
        List<Path> classpath = testClasspath()
        def kotlinRequest = { Set<Path> changed, Set<Path> deleted ->
            new CompilationRequest(SourceKind.KOTLIN, roots(SourceKind.KOTLIN), changed, deleted, false, classpath, classpath, appOut, appGenerated, []).withSiblingOutput(siblings)
        }
        String moduleFile = "META-INF/main.kotlin_module"

        when: "one run compiles both"
        def result = kotlin.compile(kotlinRequest([] as Set, [] as Set).asFull())

        then:
        result.status == CompilationResult.Status.SUCCESS
        files(appOut) == [moduleFile, "app/MainKt.class"] as Set
        files(siblings.classes()).containsAll([moduleFile, "lib/HelperKt.class", "lib/Service.class", 'lib/$Service$Definition.class'])
        files(siblings.classes()).any { it.startsWith("META-INF/micronaut/") && it.contains('lib.$Service$Definition') }
        latin(appOut.resolve(moduleFile)).contains("MainKt")
        !latin(appOut.resolve(moduleFile)).contains("HelperKt")
        latin(siblings.classes().resolve(moduleFile)).contains("HelperKt")
        !latin(siblings.classes().resolve(moduleFile)).contains("MainKt")

        when: "the application's source alone compiles against the sibling's top-level function"
        write(appSrc, "app/Main.kt", "package app\nfun main2(): String = lib.helper() + \"!\"")
        result = kotlin.compile(kotlinRequest([main] as Set, [] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString() == ["Main.kt"]
        !latin(appOut.resolve(moduleFile)).contains("HelperKt")
        latin(siblings.classes().resolve(moduleFile)).contains("HelperKt")

        when: "the sibling's source changes"
        write(libSrc, "lib/Helper.kt", "package lib\nfun helper(): String = \"two\"\nfun second(): String = \"2\"\n@jakarta.inject.Singleton\nclass Service")
        result = kotlin.compile(kotlinRequest([lib] as Set, [] as Set))

        then: "both lists stay as they should"
        result.status == CompilationResult.Status.SUCCESS
        files(appOut) == [moduleFile, "app/MainKt.class"] as Set
        latin(appOut.resolve(moduleFile)).contains("MainKt")
        !latin(appOut.resolve(moduleFile)).contains("HelperKt")
        latin(siblings.classes().resolve(moduleFile)).contains("HelperKt")

        when: "the sibling's last top-level function goes, and everything compiles again"
        write(libSrc, "lib/Helper.kt", "package lib\n@jakarta.inject.Singleton\nclass Service")
        write(appSrc, "app/Main.kt", "package app\nfun main2(): String = \"three\"")
        result = kotlin.compile(kotlinRequest([] as Set, [] as Set).asFull())

        then: "the sibling output lists no facade that is gone"
        result.status == CompilationResult.Status.SUCCESS
        !files(siblings.classes()).contains("lib/HelperKt.class")
        !Files.exists(siblings.classes().resolve(moduleFile)) || !latin(siblings.classes().resolve(moduleFile)).contains("HelperKt")
        latin(appOut.resolve(moduleFile)).contains("MainKt")
    }

    private static String latin(Path file) {
        new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1)
    }

    private void leaveClasses() {
        Path left = siblings.classes().resolve("lib/Lib.class")
        Files.createDirectories(left.parent)
        Files.writeString(left, "left by the last session")
    }

    private List<SourceRoot> roots(SourceKind kind) {
        [new SourceRoot(kind, appSrc), new SourceRoot(kind, libSrc)]
    }

    private CompilationRequest request(List<Path> classpath, Set<Path> changed = [] as Set, Set<Path> deleted = [] as Set) {
        new CompilationRequest(SourceKind.JAVA, roots(SourceKind.JAVA), changed, deleted, false, classpath, processorOnly(classpath), appOut, appGenerated, ["-parameters"]).withSiblingOutput(siblings)
    }

    private static List<Path> processorOnly(List<Path> classpath) {
        classpath == processorPath() ? classpath : []
    }

    /**
     * The test classes and resources, which hold the test processors and their service registration.
     */
    private static List<Path> processorPath() {
        Path classes = Path.of(MarkedProcessor.class.protectionDomain.codeSource.location.toURI())
        Path resources = Path.of(MarkedProcessor.class.getResource("/META-INF/services/javax.annotation.processing.Processor").toURI()).parent.parent.parent
        [classes, resources]
    }

    private static List<Path> testClasspath() {
        System.getProperty("java.class.path").split(File.pathSeparator).collect { Path.of(it) }
    }

    private static Path write(Path root, String relative, String content) {
        Path file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        file
    }

    private static Set<String> files(Path directory) {
        if (!Files.isDirectory(directory)) {
            return [] as Set
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).map { directory.relativize(it).toString() }.toList() as Set
        }
    }
}
