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
    api(projects.micronautCoreProcessor)
    api(projects.micronautInjectJava)
    api(mnSourcegen.micronaut.sourcegen.generator.java)
    api(mnSourcegen.micronaut.sourcegen.generator)
    api(mnSourcegen.micronaut.sourcegen.annotations)
    api(libs.managed.graalpy) {
        artifact {
            type = "pom"
        }
    }
    api(libs.managed.graalpy.embedding) {
        exclude(group = "org.graalvm.python", module = "python-bouncycastle-support")
    }
    compileOnly(libs.jetbrains.annotations)

    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautAop)
    testImplementation(projects.micronautContextPython)
    testImplementation(projects.micronautHttp)
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val compileVfsPythonBytecode = tasks.register<PythonVfsBytecodeCompile>("compileVfsPythonBytecode") {
    sourceDirectory.set(layout.projectDirectory.dir("src/main/resources/GRAALPY-VFS/io.micronaut/micronaut-inject-python"))
    destinationDirectory.set(layout.buildDirectory.dir("generated/resources/python-bytecode/GRAALPY-VFS/io.micronaut/micronaut-inject-python"))
    filesListPath.set("fileslist.txt")
    compilerClasspath.from(configurations.runtimeClasspath)
    compilerClasspath.from(files(tasks.compileJava.flatMap { it.destinationDirectory }))
}

tasks.processResources {
    exclude("GRAALPY-VFS/io.micronaut/micronaut-inject-python/**")
    from(compileVfsPythonBytecode) {
        into("GRAALPY-VFS/io.micronaut/micronaut-inject-python")
    }
}

noReflection {
    allowIn("io.micronaut.python.compiler.InMemoryBeanDefinitionsProvider", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.python.compiler.IncrementalCompilation", "ENUM_CONSTANTS")
    allowIn("io.micronaut.python.compiler.JavaFileObjectClassLoader", "CLASS_LOADING")
    allowIn("io.micronaut.python.compiler.PyronautJavaCompiler", "CLASS_LOADING", "CLASS_NAMES", "SERVICE_LOADING")
    allowIn("io.micronaut.python.processing.annotation.AnnotationMemberReflection", "CLASS_MEMBERS")
    allowIn("io.micronaut.python.processing.annotation.PythonAnnotationValues", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.python.processing.beans.PythonBeanDefinitionProcessor", "CLASS_NAMES")
    allowIn("io.micronaut.python.processing.element.AbstractPythonClassElement", "CLASS_NAMES")
    allowIn("io.micronaut.python.processing.element.PythonScriptElement", "SERVICE_LOADING")
    allowIn("io.micronaut.python.processing.util.PythonAnnotationTypes", "ANNOTATIONS", "CLASS_MEMBERS", "CLASS_NAMES", "ENUM_CONSTANTS")
    allowIn("io.micronaut.python.processing.visitor.LoadedVisitor", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.python.processing.visitor.PythonTypeElementVisitorProcessor", "ANNOTATIONS", "CLASS_NAMES", "SERVICE_LOADING")
}
