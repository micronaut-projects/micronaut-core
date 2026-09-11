plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)

    api(projects.micronautJacksonCore)

    compileOnly(libs.managed.graalvm.nativeimage)
    compileOnly(libs.jackson2.databind)
    compileOnly(platform(libs.test.boms.micronaut.validation))
    compileOnly(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }

    api(libs.managed.jackson.databind)
    compileOnly(libs.managed.jackson.module.kotlin) {
        isTransitive = false
    }
    compileOnly(libs.managed.jackson.module.afterburner) {
        isTransitive = false
    }

    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(libs.managed.jackson.dataformat.xml)
    testImplementation(libs.managed.snakeyaml)
    testImplementation(libs.jackson2.databind)
    testImplementation(libs.micronaut.test.junit5) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.junit.jupiter.api)
}

noReflection {
    allowIn("io.micronaut.jackson.Jackson2AnnotationSupport", "ANNOTATIONS", "CLASS_LOADING", "CLASS_MEMBERS", "ENUM_CONSTANTS", "PROXY", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.jackson.ObjectMapperFactory", "ANNOTATIONS", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.jackson.codec.JacksonFeatures", "ENUM_CONSTANTS")
    allowIn("io.micronaut.jackson.databind.DatabindPropertyBinderExceptionHandler", "CLASS_NAMES")
    allowIn("io.micronaut.jackson.databind.JacksonDatabindMapper", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.jackson.databind.convert.JacksonConverterRegistrar", "GENERIC_SIGNATURES")
}
