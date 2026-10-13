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
    // the embedded Groovy compiler exists when the project's own Groovy is on the launch classpath
    compileOnly(libs.managed.groovy)

    testImplementation(projects.micronautInjectJava)
    testImplementation(projects.micronautHttp)
    // the gate filter tests route requests to the /dev endpoint; the management module stays off the test runtime
    // classpath, whose endpoints would start with every application the tests launch
    testImplementation(projects.micronautRouter)
    testCompileOnly(projects.micronautManagement)
    testImplementation(projects.micronautInjectGroovy)
    // the compile-error page
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
    // the processors of an embedded Java compilation load from the processor path first
    allowIn("io.micronaut.dev.compile.JavacSourceCompiler", "CLASS_LOADING")
    allowIn("io.micronaut.dev.manifest.DevManifest", "ENUM_CONSTANTS")
    allowIn("io.micronaut.dev.compile.SourceKind", "ENUM_CONSTANTS")
    allowIn("io.micronaut.dev.compile.CompileMode", "ENUM_CONSTANTS")
    // MicronautDevMain.launchApplication, which looks up and invokes the application's main, suppresses the check itself
    allowIn("io.micronaut.dev.DevRuntime", "ENUM_CONSTANTS")
    allowIn("io.micronaut.dev.DevRuntime", "SERVICE_LOADING")
    allowIn("io.micronaut.dev.DevRuntime", "CLASS_LOADING")
    allowIn("io.micronaut.dev.ManifestRetentionPolicy", "CLASS_LOADING")
    allowIn("io.micronaut.dev.compile.GroovySourceCompiler", "CLASS_NAMES")
    allowIn("io.micronaut.dev.compile.GroovySourceCompiler", "CLASS_LOADING")
    allowIn("io.micronaut.dev.compile.GroovyCompilation", "CLASS_LOADING")
}
