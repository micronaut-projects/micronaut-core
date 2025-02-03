import io.micronaut.build.internal.japicmp.RemovedPackages
import me.champeau.gradle.japicmp.JapicmpTask

plugins {
    id("io.micronaut.build.internal.convention-core-library")
}

micronautBuild {
    core {
        documented = false
    }
}

dependencies {
    compileOnly(libs.managed.jakarta.annotation.api)
    compileOnly(libs.graal)
    compileOnly(libs.managed.kotlin.stdlib)
    compileOnly(libs.managed.netty.common)
}

spotless {
    java {
        targetExclude(
            "**/io/micronaut/core/io/scan/AnnotationClassReader.java",
            "**/io/micronaut/core/io/scan/Attribute.java",
            "**/io/micronaut/core/io/scan/Context.java",
            "**/io/micronaut/core/util/clhm/**",
            "**/io/micronaut/core/util/AntPathMatcher.java"
        )
    }
    format("javaMisc") {
        targetExclude("**/io/micronaut/core/util/clhm/**")
    }
}

val versionInfo = tasks.register<WriteProperties>("micronautVersionInfo") {
    destinationFile = layout.buildDirectory.file("resources/version/micronaut-version.properties")
    val projectVersion: String = project.properties["projectVersion"].toString()
    property("micronaut.version", projectVersion)
}

tasks {
    processResources {
        from(versionInfo)
    }
}

tasks.withType<JapicmpTask>().configureEach {
    richReport {
        addViolationTransformer(RemovedPackages::class.java, mapOf(
            "prefixes" to "io.micronaut.caffeine",
            "exact" to "")
        )
    }
}
