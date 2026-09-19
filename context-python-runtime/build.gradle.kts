plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    // This prototype has never been published, so no binary compatibility baseline exists.
    binaryCompatibility.enabled.set(false)
}

dependencies {
    api(projects.micronautContextPython)
    implementation(mnSourcegen.micronaut.sourcegen.bytecode.writer)
    // Sourcegen links AST API types even for reflection-backed models. No processors are invoked.
    implementation(projects.micronautCoreProcessor)
}
