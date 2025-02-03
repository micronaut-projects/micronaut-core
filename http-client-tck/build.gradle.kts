plugins {
    id("io.micronaut.build.internal.convention-core-library")
}
dependencies {
    annotationProcessor(projects.micronautInjectJava)
    api(libs.junit.jupiter)
    api(projects.micronautHttpTck)
    implementation(libs.managed.reactor)
    implementation(projects.micronautContext)
    implementation(projects.micronautHttpServerNetty)
    implementation(projects.micronautHttpClientCore)
}
tasks.named<Test>("test") {
    useJUnitPlatform()
}
