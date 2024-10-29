plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    api(projects.micronautInject)
    api(projects.micronautAop)
    api(libs.asm.tree)
    api(libs.bundles.asm)
    api(libs.managed.java.parser.core) {
        exclude(group = "org.javassist", module = "javassist")
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation(projects.micronautCoreReactive)
    api(libs.sourcegen.bytecode.generator)

    compileOnly(libs.managed.kotlin.stdlib.jdk8)
}
