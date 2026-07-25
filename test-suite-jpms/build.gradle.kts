plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    testAnnotationProcessor(projects.micronautInjectJava)

    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautHttpClientJdk)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautJacksonDatabind)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.console)
}

configurations.named("testCompileClasspath") {
    exclude(group = "io.micronaut", module = "micronaut-core-processor")
    exclude(group = "io.micronaut.sourcegen", module = "micronaut-sourcegen-model")
    exclude(group = "io.micronaut.sourcegen", module = "micronaut-sourcegen-bytecode-writer")
}

configurations.named("testRuntimeClasspath") {
    exclude(group = "io.micronaut", module = "micronaut-core-processor")
    exclude(group = "io.micronaut.sourcegen", module = "micronaut-sourcegen-model")
    exclude(group = "io.micronaut.sourcegen", module = "micronaut-sourcegen-bytecode-writer")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

val jpmsTest by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs JPMS smoke tests on module-path"

    dependsOn(tasks.named("testClasses"))

    val testModuleName = "io.micronaut.testsuite.jpms"
    val testClasses = layout.buildDirectory.dir("classes/java/test")
    val runtimeCp = configurations.testRuntimeClasspath

    mainClass.set("org.junit.platform.console.ConsoleLauncher")
    classpath = files()

    doFirst {
        val modulePath = files(testClasses.get().asFile, runtimeCp.get())
        jvmArgs(
            "--module-path", modulePath.asPath,
            "--add-modules", "ALL-MODULE-PATH",
            "--add-reads", "$testModuleName=ALL-UNNAMED"
        )
    }

    args(
        "--select-module", testModuleName,
        "--details", "tree",
        "--disable-banner"
    )
}

tasks.named("check") {
    dependsOn(jpmsTest)
}
