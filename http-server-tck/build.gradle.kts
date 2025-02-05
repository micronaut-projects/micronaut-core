plugins {
    id("io.micronaut.build.internal.convention-core-library")
}
dependencies {
    annotationProcessor(projects.micronautInjectJava)

    annotationProcessor(platform(libs.test.boms.micronaut.validation))
    annotationProcessor(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }
    annotationProcessor(projects.micronautHttpValidation)
    annotationProcessor(projects.micronautGraal)

    compileOnly(platform(libs.test.boms.micronaut.validation))
    compileOnly(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    implementation(projects.micronautRuntime)
    implementation(libs.managed.jackson.annotations)
    implementation(projects.micronautInject)
    implementation(projects.micronautManagement)
    api(projects.micronautHttpTck)
    api(projects.micronautHttpServer)
    api(projects.micronautHttpClientCore)
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.params)
    api(libs.managed.reactor)
}
micronautBuild {
    binaryCompatibility {
        enabled.set(false)
    }
}
