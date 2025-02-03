plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
	annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautInject)
    api(projects.micronautHttp)

    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautJacksonDatabind)
}
