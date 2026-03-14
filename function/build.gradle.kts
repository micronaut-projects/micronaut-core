plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
	annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautInject)
    api(projects.micronautHttp)

    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.micronaut.test.junit5) {
        exclude(group = "io.micronaut")
    }
}
