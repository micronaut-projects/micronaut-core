plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    core {
        usesMicronautTestSpock()
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    api(projects.micronautContext)

    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautInjectJavaTest)
    testCompileOnly(projects.micronautInjectGroovy)
}
