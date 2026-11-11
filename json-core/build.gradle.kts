plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautContext)
    api(projects.micronautHttp)

    testAnnotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautInjectGroovy)
    // the streamed JSON readers are tested with a mapper, and with the pooled buffers of a Netty
    // server; neither is a dependency of json-core
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautBufferNetty)
    testImplementation(libs.managed.reactor)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
}

noReflection {
    allowIn("io.micronaut.json.JsonMapper", "SERVICE_LOADING")
}
