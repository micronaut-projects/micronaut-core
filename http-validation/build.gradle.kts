plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    implementation(projects.micronautCoreProcessor)
    implementation(projects.micronautHttpServer)
    implementation(projects.micronautWebsocket)

    testCompileOnly(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectJavaTest)
}
