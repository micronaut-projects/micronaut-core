plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautContext)

    testImplementation(projects.micronautInjectJava)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("5.3.0")
    }
}

noReflection {
    // the loaders of the reloadable tier are classloaders; the manifest and the kinds parse names into enums
    allowIn("io.micronaut.dev.loader.GenerationClassLoader", "CLASS_LOADING")
    allowIn("io.micronaut.dev.loader.DevClassLoader", "CLASS_LOADING")
    allowIn("io.micronaut.dev.manifest.DevManifest", "ENUM_CONSTANTS")
    allowIn("io.micronaut.dev.compile.SourceKind", "ENUM_CONSTANTS")
    allowIn("io.micronaut.dev.compile.CompileMode", "ENUM_CONSTANTS")
}
