import io.micronaut.build.python.PythonVfsBytecodeCompile
import java.time.Duration

plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    core {
        usesMicronautTest()
    }
    binaryCompatibility {
        enabledAfter("5.2.0")
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)

    api(projects.micronautCore)
    api(projects.micronautContext)
    api(libs.managed.graalpy) {
        artifact {
            type = "pom"
        }
    }
    api(libs.managed.graalpy.embedding) {
        exclude(group = "org.graalvm.python", module = "python-bouncycastle-support")
    }
    api(libs.managed.polyglot.tools) {
        artifact {
            type = "pom"
        }
    }
    implementation(projects.micronautCoreReactive)
    compileOnlyApi(projects.micronautHttp)
    // the pythonpool management endpoint; the bean is skipped when management is absent
    compileOnly(projects.micronautManagement)
    // Mono/Flux return types of bridged methods; only used when Reactor is present at runtime
    compileOnly(libs.managed.reactor)
    compileOnly(libs.jetbrains.annotations)
    testImplementation(projects.micronautAop)
    testImplementation(projects.micronautHttp)
    testImplementation(libs.managed.reactor)
    testImplementation("com.graphql-java:java-dataloader:6.0.0")
}

val pyronautBytecodeCompiler = configurations.create("pyronautBytecodeCompiler")

dependencies {
    pyronautBytecodeCompiler(projects.micronautInjectPython)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // several GraalPy contexts live at once in the lifecycle tests; the default worker heap runs out
    maxHeapSize = "2G"
    // a test worker stuck inside GraalPy must fail the build, not hold it until the job's limit
    timeout.set(Duration.ofMinutes(30))
}

val compileVfsPythonBytecode = tasks.register<PythonVfsBytecodeCompile>("compileVfsPythonBytecode") {
    sourceDirectory.set(layout.projectDirectory.dir("src/main/resources/META-INF/GRAALPY-VFS/micronaut-application"))
    destinationDirectory.set(layout.buildDirectory.dir("generated/resources/python-bytecode/META-INF/GRAALPY-VFS/micronaut-application"))
    filesListPath.set("fileslist.txt")
    compilerClasspath.from(pyronautBytecodeCompiler, configurations.runtimeClasspath)
}

tasks.processResources {
    exclude("META-INF/GRAALPY-VFS/micronaut-application/**")
    from(compileVfsPythonBytecode) {
        into("META-INF/GRAALPY-VFS/micronaut-application")
    }
}

noReflection {
    allowIn("io.micronaut.context.python.GraalPyContextCustomizers", "SERVICE_LOADING")
    allowIn("io.micronaut.context.python.GraalPyContextFactory", "SERVICE_LOADING")
    allowIn("io.micronaut.context.python.GraalPyExceptionHandler", "CLASS_LOADING", "CLASS_MEMBERS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.context.python.GraalPyHostAccessFactory", "ANNOTATIONS", "CLASS_LOADING", "CLASS_NAMES")
    allowIn("io.micronaut.context.python.PythonCallables", "CLASS_MEMBERS", "PROXY")
    allowIn("io.micronaut.context.python.PythonCoercion", "ANNOTATIONS", "INTERFACES", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.context.python.PythonContextRuntime", "ANNOTATIONS")
    allowIn("io.micronaut.context.python.PythonConversion", "ANNOTATIONS", "CLASS_LOADING", "CLASS_MEMBERS", "ENUM_CONSTANTS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.context.python.PythonExecutorSelector", "ANNOTATIONS")
    allowIn("io.micronaut.context.python.PythonHostMembers", "CLASS_MEMBERS", "HANDLES")
    allowIn("io.micronaut.context.python.PythonHttpConversion", "CLASS_LOADING")
    allowIn("io.micronaut.context.python.PythonInterfaceDefaults", "CLASS_LOADING", "CLASS_MEMBERS", "GENERIC_SIGNATURES", "PROXY", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.context.python.PythonInvocation", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.context.python.PythonJavaBases", "CLASS_MEMBERS", "GENERIC_SIGNATURES", "INTERFACES")
    allowIn("io.micronaut.context.python.PythonPublishers", "CLASS_LOADING")
    allowIn("io.micronaut.context.python.aop.PythonProxyCreator", "ANNOTATIONS", "CLASS_MEMBERS", "REFLECTIVE_ACCESS")
}
