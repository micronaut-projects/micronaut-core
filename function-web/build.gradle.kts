plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    api(projects.micronautFunction)
    api(projects.micronautRouter)
    api(projects.micronautHttpServer)

    testImplementation(libs.managed.reactor)

    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautJacksonDatabind)
    testAnnotationProcessor(projects.micronautInjectJava)
    testCompileOnly(projects.micronautInjectGroovy)
}
