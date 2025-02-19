plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(libs.spock)
    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(libs.logback.classic)
    testImplementation(projects.micronautManagement)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautJacksonDatabind)
    testRuntimeOnly(projects.micronautHttpServerNetty)
}
