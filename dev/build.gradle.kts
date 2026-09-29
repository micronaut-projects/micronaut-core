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
    // attaches the agent to a JVM launched without -javaagent, when the project puts it on the classpath
    compileOnly(libs.bytebuddy.agent)
    // the native macOS watch service, when micronaut-runtime-osx is on the development runtime classpath
    compileOnly(libs.managed.methvin.directoryWatcher)

    testImplementation(projects.micronautInjectJava)
    testImplementation(projects.micronautHttp)
    // the gate filter tests route requests to the /dev endpoint; the management module stays off the test runtime
    // classpath, whose endpoints would start with every application the tests launch
    testImplementation(projects.micronautRouter)
    testCompileOnly(projects.micronautManagement)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(libs.bytebuddy.agent)
    testImplementation(projects.micronautRuntimeOsx)
    testAnnotationProcessor(projects.micronautInjectJava)
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Premain-Class" to "io.micronaut.dev.agent.DevAgent",
            "Agent-Class" to "io.micronaut.dev.agent.DevAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "false"
        )
    }
}

tasks.withType<Test>().configureEach {
    // the fast path test attaches the agent to the test JVM
    jvmArgs("-Djdk.attach.allowAttachSelf=true", "-XX:+EnableDynamicAgentLoading")
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
    allowIn("io.micronaut.dev.compile.GroovySourceCompiler", "CLASS_NAMES")
    allowIn("io.micronaut.dev.compile.GroovySourceCompiler", "CLASS_LOADING")
    allowIn("io.micronaut.dev.compile.GroovyCompilation", "CLASS_LOADING")
    allowIn("io.micronaut.dev.agent.DynamicAttach", "CLASS_LOADING")
    // the fast path redefines method bodies through the agent
    allowIn("io.micronaut.dev.DevRuntime", "INSTRUMENTATION")
    allowIn("io.micronaut.dev.DevWatchService", "CLASS_LOADING")
}
