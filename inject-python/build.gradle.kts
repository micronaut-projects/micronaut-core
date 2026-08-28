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
