plugins {
    id("io.micronaut.build.internal.convention-core-library")
}

micronautBuild {
    core {
        usesMicronautTestSpock()
    }
}

dependencies {
    annotationProcessor(projects.injectJava)
    api(projects.httpClientCore)
    compileOnly(projects.httpClient)
    implementation(libs.managed.reactor)
    testImplementation(projects.jacksonDatabind)
    testImplementation(projects.httpServerNetty)
    testImplementation(libs.bcpkix)
    testImplementation(libs.testcontainers.spock)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // systemProperty("jdk.httpclient.HttpClient.log", "all") // Uncomment to enable logging
}
