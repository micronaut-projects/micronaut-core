plugins {
    id("io.micronaut.build.internal.convention-core-library")
}
dependencies {
    annotationProcessor(project(":inject-java"))
    api(libs.junit.jupiter)
    api(projects.httpTck)
    implementation(libs.managed.reactor)
    implementation(project(":context"))
    implementation(project(":http-server-netty"))
    implementation(project(":http-client-core"))
}
tasks.named<Test>("test") {
    useJUnitPlatform()
}
