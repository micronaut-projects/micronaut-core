plugins {
    id("io.micronaut.build.internal.convention-library")
    alias(libs.plugins.managed.kotlin.jvm)
}

dependencies {
    compileOnly(projects.micronautInjectKotlin)
    api(libs.managed.groovy)
    api(libs.spock) {
        exclude(module = "groovy-all")
    }
    api(libs.ksp2.api)
    api(libs.ksp2)
    implementation(libs.kotlin2.compiler.embeddable)
    implementation(libs.ksp2.commonDeps)
    implementation(libs.ksp2.aaEmbeddable)
    implementation(libs.okio)
    implementation(libs.classgraph)
    testImplementation(libs.javax.persistence)
    testImplementation(projects.micronautRuntime)
    api(libs.blaze.persistence.core)
    implementation(libs.kotlin2.stdlib)
}

tasks {
    compileKotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        }
    }

    compileGroovy {
        // this allows groovy to access kotlin classes.
        classpath += files(compileKotlin.flatMap { k -> k.destinationDirectory })
    }

    test {
        jvmArgs(
            "--add-opens", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-opens", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-opens", "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-opens", "jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
            "--add-opens", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-opens", "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-opens", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens", "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
            "--add-opens", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-opens", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"
        )
    }
}
