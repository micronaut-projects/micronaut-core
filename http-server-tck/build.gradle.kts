plugins {
    id("io.micronaut.build.internal.convention-library")
}
repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor(projects.injectJava)
    annotationProcessor(projects.validation)
    implementation(projects.validation)
    implementation(projects.runtime)
    implementation(projects.inject)
    implementation(projects.management)
    api(projects.httpServer)
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.params)
    api(libs.managed.reactor)
}

java {
    sourceCompatibility = JavaVersion.toVersion("1.8")
    targetCompatibility = JavaVersion.toVersion("1.8")
}
micronautBuild {
    binaryCompatibility {
        enabled.set(false)
    }
}
