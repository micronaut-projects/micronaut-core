plugins {
    id("io.micronaut.build.internal.convention-library")
    id("application")
    id("org.graalvm.buildtools.native")
}

dependencies {
    annotationProcessor(libs.picocli.codegen)
    implementation(libs.picocli)
    implementation(projects.micronautInjectPython)
    implementation(projects.micronautContextPython)
    // For tests only, this is a temporary hack until we find how
    // to express script dependencies
    runtimeOnly(projects.micronautHttpServerNetty)
    runtimeOnly(projects.micronautJsonCore)
    runtimeOnly(projects.micronautJacksonDatabind)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass = "io.micronaut.python.cli.PyronautCliCompiler"
}

val createStartScriptsForPyronaut = tasks.register<CreateStartScripts>("createStartScriptsForPyronaut") {
    mainClass = "io.micronaut.python.cli.PyronautMainCommand"
    applicationName = "pyronaut"
    outputDir = layout.buildDirectory.dir("pyronaut/scripts").get().asFile
    classpath = files(configurations.runtimeClasspath, tasks.named("jar"))
}

distributions {
    create("pyronaut") {
        contents {
            from(createStartScriptsForPyronaut) {
                into("bin")
            }
            from(tasks.named("jar")) {
                into("lib")
            }
            from(configurations.runtimeClasspath) {
                into("lib")
            }
        }
    }
}

graalvmNative {
    binaries.all {
        sharedLibrary = false
        buildArgs.addAll(listOf(
            "--initialize-at-build-time=io.micronaut.sourcegen.model",
            "--initialize-at-build-time=io.micronaut.inject.beans.visitor",
            "--initialize-at-build-time=io.micronaut.aop.mapper",
            "--initialize-at-build-time=io.micronaut.core.reflect.ClassUtils",
            "--add-modules=jdk.unsupported",
        ))
    }
    binaries {
        named("main") {
            imageName = "pyronautc"
        }
    }
}
