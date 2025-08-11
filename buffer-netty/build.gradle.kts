plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    api(projects.micronautCore)
    api(projects.micronautInject)
    api(libs.managed.netty.buffer)
    compileOnly(libs.graal)

    annotationProcessor(projects.micronautInjectJava)

    testRuntimeOnly(projects.micronautHttpTck) // leak detection module
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
    }
}
