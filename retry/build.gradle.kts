plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautContext)
    api(projects.micronautCoreReactive)

    // Support validation annotations
    compileOnly(platform(libs.test.boms.micronaut.validation))
    compileOnly(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }

    implementation(libs.managed.reactor)

    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautDiscoveryCore)
}
