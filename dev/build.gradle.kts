plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautContext)

    // the gate filter and the compile-error page exist only when an HTTP server is present
    compileOnly(projects.micronautHttp)
    compileOnly(projects.micronautHttpServer)
    // the /dev endpoint exists only when the management module is present
    compileOnly(projects.micronautManagement)

    testImplementation(projects.micronautInjectJava)
    testImplementation(projects.micronautHttp)
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
    // the launcher loads the application's main class through the reloadable loader and invokes it
    allowIn("io.micronaut.dev.MicronautDevMain", "CLASS_LOADING")
    allowIn("io.micronaut.dev.MicronautDevMain", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.dev.MicronautDevMain", "CLASS_MEMBERS")
    allowIn("io.micronaut.dev.DevRuntime", "ENUM_CONSTANTS")
    allowIn("io.micronaut.dev.DevRuntime", "SERVICE_LOADING")
    allowIn("io.micronaut.dev.DevRuntime", "CLASS_LOADING")
    allowIn("io.micronaut.dev.ManifestRetentionPolicy", "CLASS_LOADING")
}
