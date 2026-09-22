plugins {
    id("java")
}

// A made-up web framework: its annotations and the annotation processor that describes their
// routes to the route compiler and declares them (main), and an application that uses it (test),
// compiled with the processor like any application would be. The route compiler also compiles the
// controllers of the application.
dependencies {
    implementation(projects.micronautCoreProcessor)
    implementation(projects.micronautHttp)
    implementation(projects.micronautRouterProcessor)

    testAnnotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(projects.micronautHttp)
    testAnnotationProcessor(projects.micronautRouterProcessor)
    testAnnotationProcessor(files(sourceSets.main.map { it.output.classesDirs }, sourceSets.main.map { it.output.resourcesDir!! }))

    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.logback.classic)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks {
    compileTestJava {
        dependsOn(classes)
    }
    test {
        useJUnitPlatform()
    }
}
