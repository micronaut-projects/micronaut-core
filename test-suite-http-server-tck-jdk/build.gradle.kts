plugins {
    id("io.micronaut.build.internal.convention-test-library")
}
dependencies {
    testImplementation(projects.micronautHttpServerNetty)
    implementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautHttpClientJdk)
    testImplementation(projects.micronautHttpServerTck)
    testImplementation(libs.junit.platform.engine)
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
}

tasks.withType(Test::class) {
    // systemProperty("jdk.httpclient.HttpClient.log", "all") // Uncomment to enable logging

    // These restricted headers are set in the server TCK
    systemProperty("jdk.httpclient.allowRestrictedHeaders", "Host,Connection,Content-Length")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
