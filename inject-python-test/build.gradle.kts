plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    api(projects.micronautInjectPython)
    api(projects.micronautContext)
    api(projects.micronautContextPython)
    api(libs.managed.groovy)
    api(libs.spock) {
        exclude(module = "groovy-all")
    }
    api(libs.jetbrains.annotations)
    api(libs.jakarta.inject.api)

    testImplementation(platform(libs.test.boms.micronaut.validation))
}

tasks {
    sourcesJar {
        from("$projectDir/src/main/groovy")
    }
}
