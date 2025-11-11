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
    api(projects.micronautInjectJava)
    api(mnSourcegen.micronaut.sourcegen.generator.java)
    api(mnSourcegen.micronaut.sourcegen.generator)
    api(mnSourcegen.micronaut.sourcegen.annotations)
    api(libs.managed.graalpy) {
        artifact {
            type = "pom"
        }
    }
    api(libs.managed.graalpy.embedding)
    compileOnly(libs.jetbrains.annotations)

    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautAop)
    testImplementation(projects.micronautContextPython)
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
