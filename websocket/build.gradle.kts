import java.time.Duration

plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    api(projects.micronautHttp)
    api(projects.micronautHttpClientCore)
    api(projects.micronautInject)
    api(projects.micronautAop)

    implementation(libs.managed.reactor)

    testImplementation(projects.micronautInjectGroovy)
}

tasks {
    test {
        timeout = Duration.ofMinutes(5)
    }
}
