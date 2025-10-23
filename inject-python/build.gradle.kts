plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    core {
        usesMicronautTest()
    }
}

dependencies {
    api(projects.micronautCoreProcessor)
    api(libs.managed.graalpy) {
        artifact {
            type = "pom"
        }
    }
    api(libs.managed.graalpy.embedding)
    compileOnly(libs.jetbrains.annotations)
    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautAop)
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
