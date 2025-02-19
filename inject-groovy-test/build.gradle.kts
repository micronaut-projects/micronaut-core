plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectGroovy)

    api(projects.micronautInjectGroovy)
    api(libs.managed.groovy)
    api(libs.spock) {
        exclude(module = "groovy-all")
    }
    api(projects.micronautContext)
    api(libs.jetbrains.annotations)
}

tasks {
    sourcesJar {
        from("$projectDir/src/main/groovy")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
