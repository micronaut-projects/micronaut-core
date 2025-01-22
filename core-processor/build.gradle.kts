plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    api(projects.micronautInject)
    api(projects.micronautAop)
    api(libs.managed.java.parser.core) {
        exclude(group = "org.javassist", module = "javassist")
        exclude(group = "com.google.guava", module = "guava")
    }
    api(mnSourcegen.micronaut.sourcegen.bytecode.writer)
    implementation(projects.micronautCoreReactive)

    compileOnly(libs.managed.kotlin.stdlib.jdk8)
}

