plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    core {
        usesMicronautTest()
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)

    api(projects.micronautCore)
    api(projects.micronautContext)
    api(libs.managed.graalpy) {
        artifact {
            type = "pom"
        }
    }
    api(libs.managed.graalpy.embedding)
    compileOnly(libs.jetbrains.annotations)
    testImplementation(projects.micronautAop)
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
