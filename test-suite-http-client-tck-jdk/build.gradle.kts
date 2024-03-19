plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    testImplementation(projects.httpServerNetty)
    implementation(projects.jacksonDatabind)
    testImplementation(projects.httpClientJdk)
    testImplementation(projects.httpClientTck)
    testImplementation(libs.junit.platform.engine)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // systemProperty("jdk.httpclient.HttpClient.log", "all") // Uncomment to enable logging
}
