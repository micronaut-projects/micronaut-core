plugins {
    id("java")
}

// A made-up web framework: its annotations and the annotation processor that compiles them into
// route declarations and a URL parser (main), and an application that uses it (test), compiled
// with the processor like any application would be.
dependencies {
    implementation(projects.micronautCoreProcessor)
    implementation(projects.micronautHttp)

    testAnnotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(projects.micronautHttp)
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
