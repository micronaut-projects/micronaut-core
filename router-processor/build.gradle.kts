plugins {
    id("io.micronaut.build.internal.convention-library")
}

// The route compiler: derives the routes of controllers and of custom declaration processors at
// compile time, and generates route plans, their parsers and their descriptors. A build-time
// dependency only, on the annotation processor path.
dependencies {
    annotationProcessor(projects.micronautInjectJava)
    api(projects.micronautCoreProcessor)
    api(projects.micronautRouter)
    implementation(projects.micronautHttp)

    testCompileOnly(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautHttpServer)
}

noReflection {
    // loads the route template compilers of the build, see RouteTemplateCompiler
    allowIn("io.micronaut.web.router.processor.RoutePlanCompiler", "SERVICE_LOADING")
}
