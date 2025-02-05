plugins {
    id("io.micronaut.build.internal.convention-core-library")
}

micronautBuild {
    core {
        usesMicronautTestSpock()
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    api(projects.micronautHttpClientCore)
    compileOnly(projects.micronautHttpClient)
    implementation(libs.managed.reactor)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(libs.bcpkix)
    testImplementation(libs.testcontainers.spock)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // systemProperty("jdk.httpclient.HttpClient.log", "all") // Uncomment to enable logging
}
