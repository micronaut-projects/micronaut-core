plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautContext)
    api(projects.micronautHttp)

    testAnnotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(platform(libs.test.boms.micronaut.serde))
    testImplementation("io.micronaut.serde:micronaut-serde-jsonp")
}

noReflection {
    allowIn("io.micronaut.json.JsonMapper", "SERVICE_LOADING")
}
