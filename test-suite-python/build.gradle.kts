import io.micronaut.build.internal.python.PythonCompile

plugins {
    id("io.micronaut.build.internal.convention-test-library")
    id("io.micronaut.build.internal.python")
}

dependencies {
    implementation(projects.micronautCoreProcessor)
    implementation(projects.micronautRuntime)
    implementation(projects.micronautContextPython)
    testImplementation(projects.micronautContextPythonNetty)
    testImplementation(projects.micronautInjectPython)
    testImplementation(projects.micronautInjectPythonTest)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautHttpClientCore)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautRuntime)
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }
    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautManagement)
    testImplementation(libs.micronaut.session) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.micronaut.test.junit5) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.apiguardian)

    testImplementation(platform(libs.test.boms.micronaut.data))
    testImplementation(platform(libs.test.boms.micronaut.sql))
    testImplementation("io.micronaut.data:micronaut-data-processor") {
        exclude(group = "io.micronaut")
    }
    testImplementation("io.micronaut.data:micronaut-data-jdbc") {
        exclude(group = "io.micronaut")
    }
    testImplementation("io.micronaut.data:micronaut-data-model") {
        exclude(group = "io.micronaut")
    }
    testImplementation("io.micronaut.sql:micronaut-jdbc-hikari")
    testImplementation("com.h2database:h2")
    testImplementation("jakarta.data:jakarta.data-api:1.1.0-M1")
    testImplementation(libs.managed.snakeyaml)
}

tasks.withType<Test>().configureEach {
    systemProperty("micronaut.python.pool.enabled", "false")
}

tasks.named<PythonCompile>("compileTestPython") {
    dependsOn(tasks.named("classes"))
    classpath.from(sourceSets.main.get().output)
}
