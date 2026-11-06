plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

// The embedded Python compiler of micronaut-dev, and the development runtime reloading a Python
// module: the tests compile their own fixture, so the project has no Python sources and its test
// classpath holds no generated application of its own.
dependencies {
    testImplementation(projects.micronautDev)
    testImplementation(projects.micronautInjectPython)
    testImplementation(projects.micronautContextPython)
    testImplementation(projects.micronautRuntime)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("micronaut.python.pool.enabled", "false")
    // the compiled standard library of Python, in the build rather than in the caches of the user running the tests
    val stdlibBytecode = layout.buildDirectory.dir("python-stdlib")
    doFirst {
        systemProperty("micronaut.dev.python.stdlib-bytecode-dir", stdlibBytecode.get().asFile.path)
    }
}
