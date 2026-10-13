plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautContext)

    // the gate filter and the compile-error page exist only when an HTTP server is present
    compileOnly(projects.micronautHttp)
    compileOnly(projects.micronautHttpServer)

    testImplementation(projects.micronautInjectJava)
    // the gate filter's answers and the compile-error page
    testImplementation(projects.micronautHttp)
    testImplementation(projects.micronautHttpServer)
    testAnnotationProcessor(projects.micronautInjectJava)
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
    // MicronautDevMain.launchApplication, which looks up and invokes the application's main, suppresses the check itself
    allowIn("io.micronaut.dev.DevRuntime", "ENUM_CONSTANTS")
    allowIn("io.micronaut.dev.DevRuntime", "SERVICE_LOADING")
    allowIn("io.micronaut.dev.DevRuntime", "CLASS_LOADING")
    allowIn("io.micronaut.dev.ManifestRetentionPolicy", "CLASS_LOADING")
}
