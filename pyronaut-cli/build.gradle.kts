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
            buildArgs.addAll(listOf(
                "-H:+AllowJRTFileSystem",
                "--initialize-at-run-time=com.sun.tools.javac.file.Locations",
                "--initialize-at-run-time=jdk.internal.jrtfs.SystemImage"
            ))
        }
    }
}
