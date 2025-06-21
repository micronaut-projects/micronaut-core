plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)

    api(projects.micronautJacksonCore)

    compileOnly(libs.graal)
    compileOnly(platform(libs.test.boms.micronaut.validation))
    compileOnly(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }

    api(libs.managed.jackson.databind)
    api(libs.managed.jackson.datatype.jdk8)
    api(libs.managed.jackson.datatype.jsr310)
    compileOnly(libs.managed.jackson.module.kotlin) {
        isTransitive = false
    }
    compileOnly(libs.managed.jackson.module.afterburner) {
        isTransitive = false
    }
    compileOnly(libs.managed.jackson.module.parameterNames) {
        isTransitive = false
    }

    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    testImplementation(libs.managed.snakeyaml)
    testImplementation(libs.micronaut.test.junit5) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.junit.jupiter.api)
}
