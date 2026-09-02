plugins {
    id("io.micronaut.build.internal.convention-library")
}

description = "Reflection based implementations of the metadata the Micronaut annotation processors generate"

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautInject)

    // the Java test fixtures are deliberately not processed: they are the types the processors never saw;
    // the Groovy specs and fixtures are, through the Groovy AST transformations
    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautInjectGroovy)
}
