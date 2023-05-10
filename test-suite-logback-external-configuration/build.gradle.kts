plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    testAnnotationProcessor(projects.injectJava)
    testImplementation(libs.spock)
    testImplementation(projects.context)
    testImplementation(projects.injectGroovy)
    testImplementation(libs.logback.classic)
    testImplementation(projects.management)
    testImplementation(projects.httpClient)
    testImplementation(projects.jacksonDatabind)
    testRuntimeOnly(projects.httpServerNetty)
}
