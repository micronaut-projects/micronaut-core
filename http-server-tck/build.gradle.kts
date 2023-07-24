plugins {
    id("io.micronaut.build.internal.convention-library")
}
dependencies {
    annotationProcessor(projects.injectJava)

    annotationProcessor(platform(libs.test.boms.micronaut.validation))
    annotationProcessor(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }
    annotationProcessor(projects.httpValidation)

    compileOnly(platform(libs.test.boms.micronaut.validation))
    compileOnly(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    implementation(projects.runtime)
    implementation(projects.jacksonDatabind)
    implementation(projects.inject)
    implementation(projects.management)
    api(projects.httpTck)
    api(projects.httpServer)
    api(projects.httpClientCore)
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.params)
    api(libs.managed.reactor)
}
micronautBuild {
    binaryCompatibility {
        enabled.set(false)
    }
}
