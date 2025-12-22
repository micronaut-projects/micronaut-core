plugins {
    id("io.micronaut.build.internal.convention-library")
    id("application")
    id("org.graalvm.buildtools.native")
}

repositories {
    maven {
        url = uri("https://repo.gradle.org/gradle/libs-releases")
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(libs.picocli.codegen)
    implementation(libs.picocli)
    compileOnly(projects.micronautContext)
    implementation(libs.tomlj)
    implementation(libs.gradle.tapi)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass = "io.micronaut.python.cli.PyronautMainCommand"
    applicationDefaultJvmArgs = listOf("--sun-misc-unsafe-memory-access=allow", "--enable-native-access=ALL-UNNAMED")
}

tasks {
    startScripts {
        applicationName = "pyronaut"
    }
    installDist {
        destinationDir = layout.buildDirectory.dir("install/pyronaut").get().asFile
    }
}

graalvmNative {
    binaries {
        named("main") {
            imageName = "pyronaut"
            // workaround because of the use of the library plugin
            sharedLibrary = false
            buildArgs.addAll(
                listOf(
                    "-H:+UnlockExperimentalVMOptions",
                    "-H:+AllowJRTFileSystem",
                    "--initialize-at-run-time=com.sun.tools.javac.file.Locations",
                    "--initialize-at-run-time=jdk.internal.jrtfs.SystemImage",
                    // crema
                    "-H:+RuntimeClassLoading",
                    "-H:Preserve=package=java.util",
                    "-H:Preserve=package=java.lang",
                    "-H:Preserve=package=java.io",
                    "-H:Preserve=package=java.lang.invoke",
                    "-H:Preserve=package=java.lang.constant",
                    "-H:-InterpreterTraceSupport"
                )
            )
        }
    }
}
