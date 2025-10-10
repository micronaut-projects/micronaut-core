plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    testImplementation(projects.micronautHttpServerNetty)
    implementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautHttpClientTck)
    testImplementation(libs.junit.platform.engine)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
