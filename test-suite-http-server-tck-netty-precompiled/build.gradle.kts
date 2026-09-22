plugins {
    id("java")
}

dependencies {
    testAnnotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(projects.micronautHttpValidation)
    testImplementation(projects.micronautHttpServerTck)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautManagement)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautHttpClient)
    testImplementation(libs.junit.platform.engine)
    testImplementation(libs.logback.classic)
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
    }
}
