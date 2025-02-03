plugins {
    id("io.micronaut.build.internal.convention-core-library")
}
dependencies {
    annotationProcessor(projects.micronautInjectJava)
    api(projects.micronautCoreProcessor)

    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautHttp)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(libs.managed.groovy.json)
    testImplementation(libs.javax.persistence)
    testAnnotationProcessor(projects.micronautInjectJava)
}
