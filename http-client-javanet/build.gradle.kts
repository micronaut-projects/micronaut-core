plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    core {
        usesMicronautTestSpock()
    }
}

dependencies {
    annotationProcessor(project(":inject-java"))
    implementation(project(":http-client-core"))
    implementation(libs.managed.reactor)

    testImplementation(projects.jacksonDatabind)
    testImplementation(projects.httpServerNetty)
    testImplementation(libs.bcpkix)
}
