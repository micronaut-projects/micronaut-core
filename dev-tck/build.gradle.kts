plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    api(projects.micronautDev)

    // the harness compiles the fixture with the Micronaut annotation processor on the test classpath
    testImplementation(projects.micronautInjectJava)
    testAnnotationProcessor(projects.micronautInjectJava)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("5.3.0")
    }
}
