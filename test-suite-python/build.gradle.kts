plugins {
    id("io.micronaut.build.internal.convention-test-library")
    id("io.micronaut.build.internal.python")
}

dependencies {
    implementation(projects.micronautRuntime)
    implementation(projects.micronautContextPython)
    testImplementation(projects.micronautInjectPython)
    testImplementation(projects.micronautInjectPythonTest)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautRuntime)
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }
    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautManagement)
    testImplementation(libs.micronaut.session) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.micronaut.test.junit5) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
}
