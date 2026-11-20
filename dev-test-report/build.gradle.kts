plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    api(projects.micronautDev)
    testImplementation(projects.micronautDevLivereload)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("5.3.0")
    }
}
