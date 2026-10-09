plugins {
    id("io.micronaut.build.internal.convention-core-library")
}

micronautBuild {
    core {
        usesMicronautTestSpock()
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    api(projects.micronautHttpClientCore)
    compileOnly(projects.micronautHttpClient)
    implementation(libs.managed.reactor)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(libs.bcpkix)
    testImplementation(libs.testcontainers.spock)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // systemProperty("jdk.httpclient.HttpClient.log", "all") // Uncomment to enable logging
}

// Exercise direct factory construction with the optional Netty client present.
testing {
    suites {
        register<JvmTestSuite>("testWithNetty") {
            useJUnitJupiter(libs.versions.junit5)
            dependencies {
                implementation(project())
                implementation(projects.micronautHttpClient)
                implementation(projects.micronautJacksonDatabind)
            }
        }
    }
}

tasks.named("test") {
    dependsOn(testing.suites.named("testWithNetty"))
}
