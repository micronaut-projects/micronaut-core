plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

micronautBuild {
    core {
        usesMicronautTestSpock()
    }
}

dependencies {
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectGroovyTest)
    testImplementation(projects.micronautRuntime)
    testImplementation(projects.micronautInject)
}
