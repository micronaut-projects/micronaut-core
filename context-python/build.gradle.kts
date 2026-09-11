import io.micronaut.build.internal.python.PythonVfsBytecodeCompile

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
    compileOnly(libs.jetbrains.annotations)
    testImplementation(projects.micronautAop)
    testImplementation(projects.micronautHttp)
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
    allowIn("io.micronaut.context.python.GraalPyHostAccessFactory", "CLASS_NAMES")
    allowIn("io.micronaut.context.python.PythonCoercion", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.context.python.PythonConversion", "ENUM_CONSTANTS")
    allowIn("io.micronaut.context.python.PythonExecutorSelector", "ANNOTATIONS")
    allowIn("io.micronaut.context.python.aop.PythonProxyCreator", "ANNOTATIONS", "CLASS_MEMBERS", "REFLECTIVE_ACCESS")
}
