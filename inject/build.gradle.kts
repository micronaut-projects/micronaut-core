plugins {
    id("io.micronaut.build.internal.convention-core-library")
}

micronautBuild {
    core {
        documented = false
        usesMicronautTestJunit()
    }

}

dependencies {
    compileOnly(libs.javax.inject)
    api(libs.jakarta.inject.api)
    api(libs.managed.jakarta.annotation.api)
    api(projects.micronautCore)

    compileOnly(libs.managed.snakeyaml)
    compileOnly(libs.managed.groovy)
    compileOnly(libs.managed.kotlin.stdlib.jdk8)
    compileOnly(libs.jetbrains.annotations)

    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectTestUtils)
    testImplementation(libs.systemlambda)
    testImplementation(libs.managed.snakeyaml)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.current().majorVersion.toInt() >= 21) {
        logger.warn("Opening java.util and java.lang, so SystemLambda can work")
        jvmArgs(
            listOf(
                "--add-opens", "java.base/java.util=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            )
        )
    }
}

tasks {
    checkstyleMain {
        enabled = false
    }
}

noReflection {
    allowIn("io.micronaut.context.AbstractBeanResolutionContext", "INTERFACES", "REFLECTION_UTILS")
    allowIn("io.micronaut.context.AbstractExecutable", "REFLECTION_UTILS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.context.AbstractExecutableMethod", "CLASS_NAMES")
    allowIn("io.micronaut.context.AbstractExecutableMethodsDefinition", "CLASS_NAMES", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.context.AbstractInitializableBeanDefinition", "ANNOTATION_SYNTHESIS", "CLASS_LOADING", "CLASS_NAMES", "REFLECTION_UTILS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.context.AnnotationReflectionUtils", "ANNOTATIONS", "CLASS_MEMBERS", "GENERIC_SIGNATURES", "REFLECTION_UTILS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.context.BeanLocator", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.context.BeanResolutionTraceMode", "ENUM_CONSTANTS")
    allowIn("io.micronaut.context.DefaultApplicationContextBuilder", "SERVICE_LOADING")
    allowIn("io.micronaut.context.DefaultBeanContext", "CLASS_LOADING", "CLASS_NAMES", "REFLECTION_UTILS", "TARGET_MEMBERS")
    allowIn("io.micronaut.context.DefaultFieldInjectionPoint", "ANNOTATION_SYNTHESIS", "CLASS_NAMES", "REFLECTION_UTILS")
    allowIn("io.micronaut.context.DefaultMethodInjectionPoint", "CLASS_NAMES")
    allowIn("io.micronaut.context.DefaultRuntimeBeanDefinition", "CLASS_NAMES")
    allowIn("io.micronaut.context.RuntimeBeanDefinition", "GENERIC_SIGNATURES", "INTERFACES")
    allowIn("io.micronaut.context.annotation.Primary", "CLASS_NAMES")
    allowIn("io.micronaut.context.beans.DefaultBeanDefinitionService", "FIELD_UPDATERS", "REFLECTION_UTILS")
    allowIn("io.micronaut.context.conditions.MatchesAbsenceOfClassNamesCondition", "CLASS_LOADING")
    allowIn("io.micronaut.context.conditions.MatchesCustomCondition", "REFLECTION_UTILS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.context.converters.ContextConverterRegistrar", "CLASS_LOADING")
    allowIn("io.micronaut.context.env.ConfigImportPropertySourcesLocator", "SERVICE_LOADING")
    allowIn("io.micronaut.context.env.DefaultEnvironment", "CLASS_LOADING", "SERVICE_LOADING")
    allowIn("io.micronaut.context.env.DefaultEnvironmentAndPackageDeducer", "ENUM_CONSTANTS")
    allowIn("io.micronaut.context.env.DefaultPropertyPlaceholderResolver", "SERVICE_LOADING")
    allowIn("io.micronaut.context.env.Environment", "CLASS_LOADING")
    allowIn("io.micronaut.context.i18n.ResourceBundleMessageSource", "CLASS_LOADING")
    allowIn("io.micronaut.context.scope.AbstractConcurrentCustomScope", "CLASS_NAMES")
    allowIn("io.micronaut.inject.BeanDefinition", "INTERFACES")
    allowIn("io.micronaut.inject.DelegatingExecutableMethod", "TARGET_MEMBERS")
    allowIn("io.micronaut.inject.ExecutionHandle", "TARGET_MEMBERS")
    allowIn("io.micronaut.inject.annotation.AbstractAnnotationMetadata", "ANNOTATION_SYNTHESIS", "CLASS_LOADING")
    allowIn("io.micronaut.inject.annotation.AbstractEnvironmentAnnotationMetadata", "ANNOTATION_SYNTHESIS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.inject.annotation.AnnotationConvertersRegistrar", "CLASS_LOADING", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.inject.annotation.AnnotationDefaults", "ANNOTATIONS", "CLASS_MEMBERS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.inject.annotation.AnnotationMetadataHierarchy", "ANNOTATION_SYNTHESIS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.inject.annotation.AnnotationMetadataSupport", "ANNOTATIONS", "CLASS_LOADING", "PROXY", "REFLECTION_UTILS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.inject.annotation.DefaultAnnotationMetadata", "ENUM_CONSTANTS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.inject.annotation.MappingAnnotationMetadataDelegate", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.inject.beans.AbstractEnumBeanIntrospectionAndReference", "ENUM_CONSTANTS")
    allowIn("io.micronaut.inject.beans.AbstractExecutableBeanMethod", "REFLECTION_UTILS")
    allowIn("io.micronaut.inject.beans.AbstractInitializableBeanIntrospection", "CLASS_LOADING", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.inject.qualifiers.AnnotationMetadataQualifier", "ANNOTATIONS", "CLASS_MEMBERS", "CLASS_NAMES")
    allowIn("io.micronaut.inject.qualifiers.AnnotationQualifier", "CLASS_NAMES")
    allowIn("io.micronaut.inject.qualifiers.AnnotationStereotypeQualifier", "ANNOTATIONS")
    allowIn("io.micronaut.inject.qualifiers.ClosestTypeArgumentQualifier", "GENERIC_SIGNATURES", "INTERFACES")
    allowIn("io.micronaut.inject.qualifiers.ExactTypeArgumentNameQualifier", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.inject.qualifiers.MatchArgumentQualifier", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.inject.qualifiers.NameQualifier", "CLASS_NAMES")
    allowIn("io.micronaut.inject.qualifiers.QualifierUtils", "CLASS_NAMES")
    allowIn("io.micronaut.inject.qualifiers.TypeAnnotationQualifier", "ANNOTATIONS", "CLASS_NAMES")
    allowIn("io.micronaut.inject.qualifiers.TypeArgumentQualifier", "CLASS_NAMES", "GENERIC_SIGNATURES")
}
