plugins {
    id("io.micronaut.build.internal.convention-library")
    id("application")
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
    implementation(projects.micronautInjectPython)
    implementation(projects.micronautContextPython)
    implementation(libs.tomlj)
    implementation(libs.gradle.tapi)
    // For tests only, this is a temporary hack until we find how
    // to express script dependencies
//    runtimeOnly(projects.micronautHttpServerNetty)
//    runtimeOnly(projects.micronautJsonCore)
//    runtimeOnly(projects.micronautJacksonDatabind)
//    runtimeOnly(libs.logback.classic)
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
