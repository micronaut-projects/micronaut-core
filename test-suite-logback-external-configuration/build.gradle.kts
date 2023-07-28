plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    testAnnotationProcessor(projects.injectJava)

    testImplementation(libs.managed.micronaut.test.spock) {
        exclude(group="io.micronaut", module="micronaut-aop")
    }
    testImplementation(projects.context)
    testImplementation(projects.injectGroovy)
    testImplementation(libs.managed.logback)
    testImplementation(projects.management)
    testImplementation(projects.httpClient)

    testRuntimeOnly(projects.httpServerNetty)
}
