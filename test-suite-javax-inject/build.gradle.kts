plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    testAnnotationProcessor(projects.micronautInjectJava)
    testCompileOnly(projects.micronautInjectGroovy)
    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautInject)
    testImplementation(libs.javax.inject)
    testImplementation(libs.javax.annotation.api)
}
