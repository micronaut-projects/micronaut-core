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

// the tests of the client without Netty: the default test classpath has the Netty server
testing {
    suites {
        register<JvmTestSuite>("testWithoutNetty") {
            useJUnitJupiter(libs.versions.junit5)
            dependencies {
                implementation(project())
                implementation(projects.micronautJacksonDatabind)
                implementation(libs.managed.reactor)
            }
        }
    }
}

tasks.named("test") {
    dependsOn(testing.suites.named("testWithoutNetty"))
}
