plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)
    api(projects.micronautContext)
    api(projects.micronautRetry)
    implementation(libs.managed.reactor)
    compileOnly(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
//    api(projects.micronautHttp)
}
