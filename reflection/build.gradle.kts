plugins {
    id("io.micronaut.build.internal.convention-library")
}

description = "Reflection based implementations of the metadata the Micronaut annotation processors generate"

micronautBuild {
    binaryCompatibility {
        enabledAfter("5.2.0")
    }
}

// the Groovy fixtures stand in for the classes an application hands over, which carry their parameter names
tasks.withType<GroovyCompile>().configureEach {
    groovyOptions.setParameters(true)
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautInject)

    // the Java test fixtures are deliberately not processed: they are the types the processors never saw;
    // the Groovy specs and fixtures are, through the Groovy AST transformations
    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautInjectGroovy)
}
