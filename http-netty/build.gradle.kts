plugins {
    id("io.micronaut.build.internal.convention-library")

}

micronautBuild {
    core {
        usesMicronautTestSpock()
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)
    compileOnly(libs.managed.graalvm.nativeimage)
    compileOnly(libs.managed.netty.transport.native.epoll)
    compileOnly(libs.managed.netty.transport.native.kqueue)
    compileOnly(libs.managed.netty.transport.native.iouring)
    compileOnly(libs.managed.netty.codec.http3)
    compileOnly(projects.micronautWebsocket)
    api(projects.micronautHttp)
    api(projects.micronautBufferNetty)

    api(libs.managed.netty.codec.http)
    api(libs.managed.netty.codec.http2)
    api(libs.managed.netty.handler)

    implementation(libs.managed.reactor)

    testImplementation(projects.micronautRuntime)
    testImplementation(projects.micronautWebsocket)
    testImplementation(projects.micronautJacksonDatabind)

    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(libs.junit.jupiter.params)
    testCompileOnly(projects.micronautInjectGroovy)
}

spotless {
    format("javaMisc") {
        targetExclude(
            "**/io/micronaut/http/netty/stream/package-info.java",
            "**/io/micronaut/http/netty/reactive/package-info.java"
        )
    }
}

tasks {
    // DefaultAllocatorSpec sets the io.netty.allocator system properties and needs a JVM in which
    // Netty's default allocator is not created yet, so it runs in a test JVM of its own
    val allocatorTest by registering(Test::class) {
        description = "Runs the specs configuring Netty's default allocator."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        filter {
            includeTestsMatching("io.micronaut.http.netty.allocator.DefaultAllocatorSpec")
        }
    }
    test {
        filter {
            excludeTestsMatching("io.micronaut.http.netty.allocator.DefaultAllocatorSpec")
        }
    }
    check {
        dependsOn(allocatorTest)
    }
}
