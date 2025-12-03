plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    implementation(projects.micronautCore)
    implementation(projects.micronautContextPython)
    implementation(libs.junit.platform.engine)
    implementation(libs.junit.platform.launcher)
    implementation(libs.managed.graalpy) {
        artifact {
            type = "pom"
        }
    }
    implementation(libs.managed.graalpy.embedding)
    compileOnly(libs.jetbrains.annotations)

    testImplementation(libs.junit.platform.engine)
    testImplementation(libs.junit.platform.testkit)
    testImplementation(projects.micronautInjectPython)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    val pyEnv = providers.environmentVariable("PYENV_VERSION")
    val vEnv = providers.environmentVariable("VIRTUAL_ENV")
    if (pyEnv.isPresent() && vEnv.isPresent()) {
        environment("PYENV_VERSION", pyEnv.get())
        environment("VIRTUAL_ENV", vEnv.get())
    } else {
        println("==================================================================")
        println("= WARNING: Disabling tests because not running under virtual env =")
        println("==================================================================")
        enabled = false
    }
}
