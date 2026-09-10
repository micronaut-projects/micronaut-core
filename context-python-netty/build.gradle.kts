plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    core {
        usesMicronautTest()
    }
    binaryCompatibility {
        enabledAfter("5.2.0")
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)

    api(projects.micronautContextPython)
    api(projects.micronautHttpNetty)
    implementation(projects.micronautHttpServerNetty)
    implementation(libs.managed.netty.resolver.dns)

    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(libs.managed.netty.pkitesting)
    // the transport tests run over every native transport available on the machine
    testImplementation(libs.managed.netty.transport.native.epoll) {
        artifact {
            classifier = "linux-x86_64"
        }
    }
    testImplementation(libs.managed.netty.transport.native.kqueue) {
        artifact {
            classifier = if (org.apache.tools.ant.taskdefs.condition.Os.isArch("aarch64")) {
                "osx-aarch_64"
            } else {
                "osx-x86_64"
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
