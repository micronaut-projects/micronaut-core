plugins {
    id("io.micronaut.build.internal.convention-library")
}

description = "A support library which defines types used to declare Micronaut module information"

configurations.all {
    // Note: this module doesn't depend on core and is independent, so that it can be used by build tools
    // for example, without depending on Micronaut Core
    // This block will remove dependencies added automatically by Micronaut Build (e.g slf4j)
    dependencies.removeAll { true }
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("5.0.0")
    }
}

tasks.named("ossIndexAudit") {
    // the task fails if there are no dependencies (:facepalm:)
    enabled = false
}
