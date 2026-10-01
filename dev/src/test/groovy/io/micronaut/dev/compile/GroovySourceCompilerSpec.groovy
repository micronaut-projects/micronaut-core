package io.micronaut.dev.compile

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GroovySourceCompilerSpec extends Specification {

    @TempDir
    Path project

    Path src
    Path out
    GroovySourceCompiler compiler = new GroovySourceCompiler()

    def setup() {
        src = Files.createDirectories(project.resolve("src/main/groovy"))
        out = project.resolve("build/classes")
        write("example/Base.groovy", "package example\nclass Base { String name() { 'base' } }")
        write("example/Derived.groovy", "package example\nclass Derived extends Base { String name() { 'derived:' + super.name() } }")
        write("example/Service.groovy", "package example\n@jakarta.inject.Singleton\nclass Service { String hello() { 'one' } }")
    }

    void "the compiler is available with groovy on the classpath and is registered as a service"() {
        expect:
        compiler.isAvailable()
        compiler.kinds() == [SourceKind.GROOVY] as Set
        ServiceLoader.load(SourceCompiler).any { it instanceof GroovySourceCompiler }
    }

    void "a full compilation runs the micronaut transformations and records what each source produced"() {
        when:
        def result = compiler.compile(request().asFull())

        then:
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources.size() == 3
        Files.exists(out.resolve("example/Base.class"))
        Files.exists(out.resolve("example/Derived.class"))
        Files.exists(out.resolve("example/Service.class"))
        Files.exists(out.resolve('example/$Service$Definition.class'))
        Files.exists(out.resolve("META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference"))
        Files.readString(SourceIndex.mappingFile(out, SourceKind.GROOVY)).contains("example.Service")
        loadAndCall("example.Derived", "name") == "derived:base"
    }

    void "an incremental compilation recompiles the dependents and a deleted class takes its outputs with it"() {
        given:
        compiler.compile(request().asFull())

        when: "the base class changes"
        Path base = write("example/Base.groovy", "package example\nclass Base { String name() { 'changed' } }")
        def result = compiler.compile(request([base] as Set, [] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString().toSet() == ["Base.groovy", "Derived.groovy"] as Set
        loadAndCall("example.Derived", "name") == "derived:changed"

        when: "the service is deleted"
        Path service = src.resolve("example/Service.groovy")
        Files.delete(service)
        result = compiler.compile(request([] as Set, [service] as Set))

        then: "its class, its definition and its index entry are gone"
        result.status == CompilationResult.Status.NOTHING_TO_DO
        !Files.exists(out.resolve("example/Service.class"))
        !Files.exists(out.resolve('example/$Service$Definition.class'))
        Files.list(out.resolve("META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference")).noneMatch { it.fileName.toString().contains("Service") }
    }

    void "a broken edit reports the error with its position and leaves the output as it was"() {
        given:
        compiler.compile(request().asFull())
        byte[] before = Files.readAllBytes(out.resolve("example/Base.class"))

        when:
        Path base = write("example/Base.groovy", "package example\nclass Base { String name() { return 1 +* } }")
        def result = compiler.compile(request([base] as Set, [] as Set))

        then:
        result.status == CompilationResult.Status.FAILED
        result.errors().first().file() == base
        result.errors().first().line() == 2
        Files.readAllBytes(out.resolve("example/Base.class")) == before
    }

    void "a deleted class is hidden from its dependents"() {
        given:
        compiler.compile(request().asFull())
        Path base = src.resolve("example/Base.groovy")
        Files.delete(base)

        when:
        def result = compiler.compile(request([] as Set, [base] as Set))

        then: "the derived class cannot compile against the stale class file"
        result.status == CompilationResult.Status.FAILED
        Files.exists(out.resolve("example/Base.class"))
    }

    private Path write(String relative, String source) {
        Path file = src.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, source)
        file
    }

    private Object loadAndCall(String className, String method) {
        try (URLClassLoader loader = new URLClassLoader([out.toUri().toURL()] as URL[], getClass().classLoader)) {
            Class<?> type = loader.loadClass(className)
            return type.getMethod(method).invoke(type.getDeclaredConstructor().newInstance())
        }
    }

    private CompilationRequest request(Set<Path> changed = [] as Set, Set<Path> deleted = [] as Set) {
        List<Path> classpath = System.getProperty("java.class.path").split(File.pathSeparator).collect { Path.of(it) }
        new CompilationRequest(SourceKind.GROOVY, [new SourceRoot(SourceKind.GROOVY, src)], changed, deleted, false, classpath, [], out, project.resolve("build/generated"), [])
    }
}
