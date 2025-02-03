plugins {
    id("io.micronaut.build.internal.convention-core-library")
}

micronautBuild {
    core {
        documented = false
        usesMicronautTestJunit()
    }

}

dependencies {
    compileOnly(libs.javax.inject)
    api(libs.jakarta.inject.api)
    api(libs.managed.jakarta.annotation.api)
    api(projects.micronautCore)

    compileOnly(libs.managed.snakeyaml)
    compileOnly(libs.managed.groovy)
    compileOnly(libs.managed.kotlin.stdlib.jdk8)

    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectTestUtils)
    testImplementation(libs.systemlambda)
    testImplementation(libs.managed.snakeyaml)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.current().majorVersion.toInt() >= 17) {
        logger.warn("Opening java.util and java.lang, so SystemLambda can work")
        jvmArgs(
            listOf(
                "--add-opens", "java.base/java.util=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            )
        )
    }
}

tasks {
    checkstyleMain {
        enabled = false
    }
}
