plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    implementation(libs.managed.reactor)

    api(projects.micronautHttp)
    api(projects.micronautJsonCore)
    api(projects.micronautDiscoveryCore)

    compileOnly(libs.managed.kotlin.stdlib)

    testImplementation(projects.micronautJacksonDatabind)
}

//tasks.withType(Test).configureEach {
//    testLogging {
//        showStandardStreams = true
//        exceptionFormat = 'full'
//    }
//}
//
