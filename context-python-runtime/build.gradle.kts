plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    // This module has never been published, so no binary compatibility baseline exists.
    binaryCompatibility.enabled.set(false)
}

dependencies {
    // Only the framework runtime: the module serves any Python application classpath and must stay out of the
    // context-python build graph, which compiles its own Python resources with inject-python.
    api(projects.micronautInject)
    // The runtime backend emits bytecode with ASM directly: Sourcegen's model links the compiler AST API
    // (io.micronaut.inject.ast) from a dozen classes, which would put core-processor on the application classpath.
    implementation(mnSourcegen.asm)
    testImplementation(libs.junit.jupiter)
    testImplementation(mnSourcegen.asm.util)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
