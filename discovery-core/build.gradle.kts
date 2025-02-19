plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)
    api(projects.micronautContext)
    implementation(libs.managed.reactor)
    compileOnly(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautJacksonDatabind)
//    api(projects.micronautHttp)
}
