plugins {
    id("io.micronaut.build.internal.convention-core-library")
    id("io.micronaut.build.internal.kotlin-base")
    alias(libs.plugins.managed.kotlin.jvm)
}

micronautBuild {
    core {
        documented = false
    }
}

dependencies {
    api(projects.micronautInject)
    api(projects.micronautCore)
    compileOnly(projects.micronautCoreReactive)
    compileOnly(libs.managed.kotlinx.coroutines.core)
    compileOnly(libs.managed.reactor)
}

tasks {
    test {
        // there are no real tests in this project
        failOnNoDiscoveredTests = false
    }
}

noReflection {
    allowIn("io.micronaut.aop.beandefinition.InitializableInterceptedMethod", "TARGET_MEMBERS")
    allowIn("io.micronaut.aop.beandefinition.InterceptedDisposeMethod", "TARGET_MEMBERS")
    allowIn("io.micronaut.aop.beandefinition.InterceptedMethod", "CLASS_NAMES")
    allowIn("io.micronaut.aop.chain.InterceptorChain", "ANNOTATIONS")
    allowIn("io.micronaut.aop.chain.MethodInterceptorChain", "TARGET_MEMBERS")
    allowIn("io.micronaut.aop.internal.intercepted.PublisherInterceptedMethod", "CLASS_LOADING")
    allowIn("io.micronaut.aop.internal.intercepted.ReactorInterceptedMethod", "CLASS_LOADING")
}
