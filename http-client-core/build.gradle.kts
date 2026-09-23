plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    implementation(libs.managed.reactor)

    api(projects.micronautHttp)
    api(projects.micronautJsonCore)
    api(projects.micronautDiscoveryCore)

    compileOnly(libs.managed.kotlin.stdlib)
    compileOnly(libs.managed.kotlinx.coroutines.core)

    testImplementation(projects.micronautJacksonDatabind)
}

//tasks.withType(Test).configureEach {
//    testLogging {
//        showStandardStreams = true
//        exceptionFormat = 'full'
//    }
//}
//

noReflection {
    allowIn("io.micronaut.http.client.HttpClientFactoryResolver", "SERVICE_LOADING")
    allowIn("io.micronaut.http.client.ProxyHttpClientFactoryResolver", "SERVICE_LOADING")
    allowIn("io.micronaut.http.client.RawHttpClientFactoryResolver", "SERVICE_LOADING")
    allowIn("io.micronaut.http.client.StreamingHttpClientFactoryResolver", "SERVICE_LOADING")
    allowIn("io.micronaut.http.client.interceptor.HttpClientIntroductionAdvice", "CLASS_NAMES")
    allowIn("io.micronaut.http.client.sse.SseClientFactoryResolver", "SERVICE_LOADING")
}
