plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    api(libs.managed.groovy)
    api(libs.spock) {
        exclude(module="groovy-all")
    }
    api(projects.micronautCoreProcessor)
}
