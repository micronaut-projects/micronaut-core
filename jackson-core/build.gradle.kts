plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautJsonCore)

    api(libs.managed.jackson.core)
    api(libs.managed.jackson.annotations)
    compileOnly(libs.managed.netty.buffer)

    testAnnotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautInjectGroovy)
}
