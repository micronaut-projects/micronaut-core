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
    test {
        forkEvery = 1
    }
}

noReflection {
    allowIn("io.micronaut.http.netty.channel.converters.DefaultChannelOptionFactory", "GENERIC_SIGNATURES", "REFLECTION_UTILS")
    allowIn("io.micronaut.http.netty.channel.loom.LoomBranchSupport", "CLASS_LOADING", "HANDLES")
    allowIn("io.micronaut.http.netty.channel.loom.PrivateLoomSupport", "CLASS_LOADING", "CLASS_MEMBERS", "HANDLES", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.http.netty.cookies.NettyCookie", "ENUM_CONSTANTS")
    allowIn("io.micronaut.http.netty.websocket.AbstractNettyWebSocketHandler", "CLASS_NAMES")
}
