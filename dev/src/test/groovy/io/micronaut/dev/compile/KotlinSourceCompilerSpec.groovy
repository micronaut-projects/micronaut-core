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
        Path gadget = write("example/Gadget.kt", "package example\n@Marked\nclass Gadget { val size: Int = 1 }")

        when:
        def result = compiler.compile(request().asFull())

        then: "the generated Kotlin and Java classes were compiled and the resource staged"
        result.status == CompilationResult.Status.SUCCESS
        Files.exists(out.resolve("example/WidgetGenerated.class"))
        Files.exists(out.resolve("example/WidgetSupport.class"))
        Files.exists(out.resolve("META-INF/generated/example.Widget"))
        Files.readString(out.resolve("META-INF/generated/example.Gadget.members")) == "1 functions, 1 properties"
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
        !Files.exists(out.resolve("META-INF/generated/example.Widget.members"))
        Files.exists(out.resolve("META-INF/generated/example.Gadget.members"))
        !Files.exists(project.resolve("build/generated/example/WidgetGenerated.kt"))
    }

    void "a bean inheriting injection points keeps its generated classes when its superclass compiles"() {
        given: "a bean whose definition KSP may credit to the source of its superclass too, found first"
        write("example/AParent.kt", "package example\nabstract class AParent { @jakarta.inject.Inject var helper: example.Service? = null }")
        write("example/ZChild.kt", "package example\n@jakarta.inject.Singleton\nclass ZChild : AParent()")
        compiler.compile(request().asFull())

        when: "the superclass is edited"
        Path parent = write("example/AParent.kt", "package example\nabstract class AParent { @jakarta.inject.Inject var helper: example.Service? = null\n // edit\n }")
        def result = compiler.compile(request([parent] as Set, [] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString().containsAll(["AParent.kt", "ZChild.kt"])
        Files.exists(out.resolve("example/ZChild.class"))
        Files.exists(out.resolve('example/$ZChild$Definition.class'))
    }

    void "an incremental compilation keeps the top-level declarations of the sources it did not compile"() {
        given: "two sources of top-level functions, compiled"
        write("demo/A.kt", "package demo\nfun a(): String = \"a\"")
        write("demo/B.kt", "package demo\nfun b(): String = \"b\"")
        Path e = write("demo/E.kt", "package demo\nfun e(): String = \"e\"")
        compiler.compile(request().asFull())

        when: "one of them is compiled alone"
        Path a = write("demo/A.kt", "package demo\nfun a(): String = \"a2\"")
        def result = compiler.compile(request([a] as Set, [] as Set))

        then:
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString() == ["A.kt"]

        when: "a new source calls both"
        Path c = write("demo/C.kt", "package demo\nclass C { fun name(): String = b() + a() }")
        result = compiler.compile(request([c] as Set, [] as Set))

        then: "the function of the source not compiled since the full compilation still resolves"
        result.status == CompilationResult.Status.SUCCESS
        loadAndCall("demo.C", "name") == "ba2"

        when: "a source of top-level functions is deleted with its caller's call, and another source calls what is left"
        Files.delete(a)
        write("demo/C.kt", "package demo\nclass C { fun name(): String = b() }")
        def deletion = compiler.compile(request([c] as Set, [a] as Set))
        Path d = write("demo/D.kt", "package demo\nclass D { fun name(): String = b() }")
        result = compiler.compile(request([d] as Set, [] as Set))

        then:
        deletion.status == CompilationResult.Status.SUCCESS
        result.status == CompilationResult.Status.SUCCESS
        loadAndCall("demo.D", "name") == "b"
        !Files.exists(out.resolve("demo/AKt.class"))

        when: "a source of top-level functions no other calls is deleted alone, which compiles nothing"
        Files.delete(e)
        deletion = compiler.compile(request([] as Set, [e] as Set))
        Path f = write("demo/F.kt", "package demo\nclass F { fun name(): String = b() }")
        result = compiler.compile(request([f] as Set, [] as Set))

        then: "the module metadata written without a compilation still reads"
        deletion.status != CompilationResult.Status.FAILED
        result.status == CompilationResult.Status.SUCCESS
        loadAndCall("demo.F", "name") == "b"
    }

    void "a symbol processor's warnings and errors are diagnostics at the class they name, and it receives the processor options"() {
        given:
        write("example/Marked.kt", "package example\nannotation class Marked")
        Path warned = write("example/Warned.kt", "package example\n@Marked\nclass Warned")
        List<String> options = ["-Aexample.flag=on", "-Aexample.bare", "-jvm-target", "21", "-api-version=2.0"]

        when:
        def result = compiler.compile(request([] as Set, [] as Set, options).asFull())

        then: "the warning names the class's file, and the options other than the processors' went to kotlinc"
        result.status == CompilationResult.Status.SUCCESS
        result.diagnostics.any { it.severity() == CompileDiagnostic.Severity.WARNING && it.file() == warned && it.message().contains("Warned is warned") }
        Files.readString(out.resolve("META-INF/generated/options")) == "example.bare=\nexample.flag=on\n"
        Files.exists(out.resolve("example/WarnedGenerated.class"))

        when:
        Path refused = write("example/Refused.kt", "package example\n@Marked\nclass Refused")
        result = compiler.compile(request([refused] as Set, [] as Set, options))

        then:
        result.status == CompilationResult.Status.FAILED
        result.diagnostics.any { it.severity() == CompileDiagnostic.Severity.ERROR && it.file() == refused && it.message().contains("Refused is refused") }
        !Files.exists(out.resolve("example/RefusedGenerated.class"))
    }

    void "the symbol processors resolve the classes of the JDK, from the JDK the options name or else from this JVM's"() {
        given: "a bean injecting a star projection of a Java generic type, and an introduction of a generic interface: the processors bound their type parameters by java.lang.Object"
        write("example/Publishing.kt", "package example\n@jakarta.inject.Singleton\nclass Publishing(val publisher: io.micronaut.context.event.ApplicationEventPublisher<*>)")
        write("example/Repo.kt", """package example
@io.micronaut.aop.Introduction
@io.micronaut.context.annotation.Type(Finder::class)
@Retention(AnnotationRetention.RUNTIME)
annotation class Found
@jakarta.inject.Singleton
class Finder : io.micronaut.aop.MethodInterceptor<Any, Any> { override fun intercept(context: io.micronaut.aop.MethodInvocationContext<Any, Any>): Any? = null }
interface Finding<E, ID> { fun find(id: ID): E? }
@Found
interface Repo : Finding<String, Long>
""")

        when:
        def result = compiler.compile(request([] as Set, [] as Set, options).asFull())

        then:
        result.status == CompilationResult.Status.SUCCESS
        Files.exists(out.resolve('example/$Publishing$Definition.class'))
        Files.list(out.resolve("example")).anyMatch { it.fileName.toString().startsWith('$Repo$Intercepted') }

        where:
        options << [[], ["-jdk-home", System.getProperty("java.home")]]
    }

    void "a source calling an internal member of a source not compiled with it compiles, in the module the build named"() {
        given: "the module as the build compiled it, under the name the build gives"
        List<String> options = ["-module-name", "io.example:hello-kt"]
        write("demo/Secret.kt", "package demo\nclass Secret { internal fun value(): String = \"secret\" }")
        write("demo/Facts.kt", "package demo\nfun fact(): String = \"fact\"")
        Path user = write("demo/User.kt", "package demo\nclass User { fun name(): String = Secret().value() }")
        compiler.compile(request([] as Set, [] as Set, options).asFull())
        Path moduleFile = out.resolve(KotlinModuleMapping.relativeFile("io.example:hello-kt"))

        when: "the caller is edited and compiled alone"
        write("demo/User.kt", "package demo\nclass User { fun name(): String = Secret().value() + \"!\" }")
        def result = compiler.compile(request([user] as Set, [] as Set, options))

        then: "the previous output is of the same module, so the internal member is visible and links"
        result.status == CompilationResult.Status.SUCCESS
        result.compiledSources*.fileName*.toString() == ["User.kt"]
        loadAndCall("demo.User", "name") == "secret!"

        when: "a new source calls the top-level function of a source not compiled since"
        Path caller = write("demo/Caller.kt", "package demo\nclass Caller { fun name(): String = fact() }")
        result = compiler.compile(request([caller] as Set, [] as Set, options))

        then: "the module metadata, where kotlinc writes it, kept listing its file facade, and it is the only one"
        result.status == CompilationResult.Status.SUCCESS
        loadAndCall("demo.Caller", "name") == "fact"
        Files.exists(moduleFile)
        Files.list(moduleFile.parent).filter { it.fileName.toString().endsWith(".kotlin_module") }.count() == 1
    }

    void "the module name is the last the options give, in either form, and main without one"() {
        expect:
        KotlinCompilation.moduleName(options) == expected

        where:
        options                                    | expected
        []                                         | "main"
        ["-module-name"]                           | "main"
        ["-module-name", "app"]                    | "app"
        ["-module-name=app"]                       | "app"
        ["-module-name", "one", "-module-name=two"] | "two"
        ["-jvm-target", "21", "-module-name", "app"] | "app"
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

    private CompilationRequest request(Set<Path> changed = [] as Set, Set<Path> deleted = [] as Set, List<String> options = []) {
        List<Path> classpath = System.getProperty("java.class.path").split(File.pathSeparator).collect { Path.of(it) }
        new CompilationRequest(SourceKind.KOTLIN, [new SourceRoot(SourceKind.KOTLIN, src)], changed, deleted, false, classpath, classpath, out, project.resolve("build/generated"), options)
    }
}
