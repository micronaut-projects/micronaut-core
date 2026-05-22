plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    core {
        usesMicronautTest()
    }
    binaryCompatibility {
        enabledAfter("5.1.0")
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
    implementation(projects.micronautCoreReactive)
    compileOnlyApi(projects.micronautHttp)
    compileOnly(libs.jetbrains.annotations)
    testImplementation(projects.micronautAop)
    testImplementation(projects.micronautHttp)
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
