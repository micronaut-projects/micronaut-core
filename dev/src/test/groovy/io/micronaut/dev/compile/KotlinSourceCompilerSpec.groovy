package io.micronaut.dev.compile

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class KotlinSourceCompilerSpec extends Specification {

    @TempDir
    Path project

    Path src
    Path out
    KotlinSourceCompiler compiler = new KotlinSourceCompiler()

    def setup() {
        src = Files.createDirectories(project.resolve("src/main/kotlin"))
        out = project.resolve("build/classes")
        write("example/Base.kt", "package example\nopen class Base { open fun name(): String = \"base\" }")
        write("example/Derived.kt", "package example\nclass Derived : Base() { override fun name(): String = \"derived:\" + super.name() }")
        write("example/Service.kt", "package example\n@jakarta.inject.Singleton\nclass Service { fun hello(): String = \"one\" }")
    }

    void "the compiler is available with the build tools api on the classpath and is registered as a service"() {
        expect:
        compiler.isAvailable()
        compiler.kinds() == [SourceKind.KOTLIN] as Set
        ServiceLoader.load(SourceCompiler).any { it instanceof KotlinSourceCompiler }
    }

    void "a full compilation runs the micronaut symbol processors and records what each source produced"() {
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
        Files.readString(SourceIndex.mappingFile(out, SourceKind.KOTLIN)).contains("example.Service")
        loadAndCall("example.Derived", "name") == "derived:base"
    }

    void "an incremental compilation recompiles the dependents and a deleted class takes its outputs with it"() {
        given:
        compiler.compile(request().asFull())

        when: "the base class changes"
        Path base = write("example/Base.kt", "package example\nopen class Base { open fun name(): String = \"changed\" }")
        def result = compiler.compile(request([base] as Set, [] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString().toSet() == ["Base.kt", "Derived.kt"] as Set
        loadAndCall("example.Derived", "name") == "derived:changed"
        Files.exists(out.resolve('example/$Service$Definition.class'))

        when: "the service is deleted"
        Path service = src.resolve("example/Service.kt")
        Files.delete(service)
        result = compiler.compile(request([] as Set, [service] as Set))

        then: "its class, its definition and its index entry are gone, and the processors ran for the deletion"
        result.status == CompilationResult.Status.SUCCESS
        !Files.exists(out.resolve("example/Service.class"))
        !Files.exists(out.resolve('example/$Service$Definition.class'))
        Files.list(out.resolve("META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference")).noneMatch { it.fileName.toString().contains("Service") }
    }

    void "a broken edit reports the error with its position and leaves the output as it was"() {
        given:
        compiler.compile(request().asFull())
        byte[] before = Files.readAllBytes(out.resolve("example/Base.class"))

        when:
        Path base = write("example/Base.kt", "package example\nopen class Base { open fun name(): String = 1 +* }")
        def result = compiler.compile(request([base] as Set, [] as Set))

        then:
        result.status == CompilationResult.Status.FAILED
        result.diagnostics.any { it.severity() == CompileDiagnostic.Severity.ERROR && it.file() == base && it.line() == 2 }
        Files.readAllBytes(out.resolve("example/Base.class")) == before
    }

    void "what a symbol processor generates for a source is credited to it and goes when the source goes"() {
        given:
        write("example/Marked.kt", "package example\nannotation class Marked")
        Path widget = write("example/Widget.kt", "package example\n@Marked\nclass Widget")
        Path gadget = write("example/Gadget.kt", "package example\n@Marked\nclass Gadget")

        when:
        def result = compiler.compile(request().asFull())

        then: "the generated Kotlin and Java classes were compiled and the resource staged"
        result.status == CompilationResult.Status.SUCCESS
        Files.exists(out.resolve("example/WidgetGenerated.class"))
        Files.exists(out.resolve("example/WidgetSupport.class"))
        Files.exists(out.resolve("META-INF/generated/example.Widget"))
        Files.exists(project.resolve("build/generated/example/WidgetGenerated.kt"))
        loadAndCall("example.WidgetGenerated", "name") == "generated:Widget"
        loadAndCall("example.WidgetSupport", "name") == "java:Widget"
        Files.readString(SourceIndex.mappingFile(out, SourceKind.KOTLIN)).contains("example.WidgetGenerated")
        Files.readString(out.resolve("META-INF/generated/all-marked")) == "example.Gadget\nexample.Widget"

        when: "an annotated source is deleted, with nothing else to compile"
        Files.delete(widget)
        result = compiler.compile(request([] as Set, [widget] as Set))

        then: "what was generated for it is gone, and the output over every source was generated again without it"
        result.status == CompilationResult.Status.SUCCESS
        Files.readString(out.resolve("META-INF/generated/all-marked")) == "example.Gadget"
        Files.exists(out.resolve("example/GadgetGenerated.class"))
        !Files.exists(out.resolve("example/WidgetGenerated.class"))
        !Files.exists(out.resolve("example/WidgetSupport.class"))
        !Files.exists(out.resolve("META-INF/generated/example.Widget"))
        !Files.exists(project.resolve("build/generated/example/WidgetGenerated.kt"))
    }

    private Path write(String relative, String source) {
        Path file = src.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, source)
        file
    }

    private Object loadAndCall(String className, String method) {
        try (URLClassLoader loader = new URLClassLoader([out.toUri().toURL()] as URL[], KotlinSourceCompilerSpec.classLoader)) {
            Class<?> type = loader.loadClass(className)
            return type.getMethod(method).invoke(type.getDeclaredConstructor().newInstance())
        }
    }

    private CompilationRequest request(Set<Path> changed = [] as Set, Set<Path> deleted = [] as Set) {
        List<Path> classpath = System.getProperty("java.class.path").split(File.pathSeparator).collect { Path.of(it) }
        new CompilationRequest(SourceKind.KOTLIN, [new SourceRoot(SourceKind.KOTLIN, src)], changed, deleted, false, classpath, processorPath(), out, project.resolve("build/generated"), [])
    }

    /**
     * The classpath entries that register a symbol processor: the Micronaut processor and the test one.
     * The processors are found on the processor path alone, so the entries holding their service files are it.
     */
    private static List<Path> processorPath() {
        String service = "META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider"
        KotlinSourceCompilerSpec.classLoader.getResources(service).toList().collect { URL url ->
            String location = url.toString()
            if (location.startsWith("jar:")) {
                return Path.of(new URI(location.substring(4, location.indexOf("!"))))
            }
            return Path.of(url.toURI()).parent.parent.parent
        }.unique()
    }
}
