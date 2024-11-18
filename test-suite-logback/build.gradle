plugins {
    id("io.micronaut.build.internal.convention-test-library")
}
micronautBuild {
    core {
        usesMicronautTestSpock()
    }
}

dependencies {
    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautContext)

    testImplementation(projects.micronautInjectGroovy)
    testImplementation(libs.logback.classic)
    testImplementation(libs.managed.reactor.test);

    testImplementation(projects.micronautManagement)
    testImplementation(projects.micronautHttpClient)

    testRuntimeOnly(projects.micronautHttpServerNetty)
    testRuntimeOnly(projects.micronautJacksonDatabind)
}
