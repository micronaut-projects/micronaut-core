plugins {
    id("io.micronaut.build.internal.convention-library")
}

description = "Exposes loaded Micronaut modules as runtime information regular beans"

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    implementation(projects.micronautInject)
    implementation(projects.micronautModuleInfo)

    testAnnotationProcessor(projects.micronautInjectJava)

}

micronautBuild {
    binaryCompatibility {
        enabledAfter("5.0.0")
    }
}
