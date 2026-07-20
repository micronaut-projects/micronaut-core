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
    api(libs.managed.polyglot.tools) {
        artifact {
            type = "pom"
        }
    }
    implementation(projects.micronautCoreReactive)
    compileOnlyApi(projects.micronautHttp)
    compileOnly(libs.jetbrains.annotations)
    testImplementation(projects.micronautAop)
    testImplementation(projects.micronautHttp)
    testImplementation("com.graphql-java:java-dataloader:6.0.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
