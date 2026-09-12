plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautInject)
    api(projects.micronautHttp)
    compileOnly(libs.managed.groovy)

    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectJava)
    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectGroovyTest)
    testImplementation(libs.jazzer.junit)
    testImplementation(libs.jazzer.api)
    testImplementation(libs.icu4j)
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_25)) {
        // Jazzer 0.24.0 cannot instrument JDK 25 class files (major version 69).
        exclude("**/UriUtilTest.class")
    }
}

//compileTestGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
//compileTestGroovy.groovyOptions.fork = true

noReflection {
    allowIn("io.micronaut.web.router.AbstractRouteMatch", "TARGET_MEMBERS")
    allowIn("io.micronaut.web.router.DefaultErrorRouteInfo", "CLASS_NAMES")
    allowIn("io.micronaut.web.router.DefaultRouteBuilder", "CLASS_NAMES")
    allowIn("io.micronaut.web.router.DefaultRouter", "CLASS_NAMES", "ENUM_CONSTANTS", "INTERFACES")
    allowIn("io.micronaut.web.router.DefaultStatusRouteInfo", "CLASS_NAMES")
    allowIn("io.micronaut.web.router.DefaultUrlRouteInfo", "CLASS_NAMES")
    allowIn("io.micronaut.web.router.RouteBuilder", "ANNOTATIONS", "CLASS_NAMES")
    allowIn("io.micronaut.web.router.Router", "ENUM_CONSTANTS")
    allowIn("io.micronaut.web.router.exceptions.UnsatisfiedRouteException", "CLASS_NAMES")
}
