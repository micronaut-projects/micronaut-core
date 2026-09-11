import io.micronaut.build.internal.japicmp.RemovedPackages
import me.champeau.gradle.japicmp.JapicmpTask

plugins {
    id("io.micronaut.build.internal.convention-core-library")
}

micronautBuild {
    core {
        documented = false
    }
}

dependencies {
    api(libs.managed.jspecify)
    compileOnly(libs.managed.jakarta.annotation.api)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.managed.graalvm.nativeimage)
    compileOnly(libs.managed.kotlin.stdlib)
    compileOnly(libs.managed.netty.common)
    testImplementation(libs.junit.jupiter.params)
}

spotless {
    java {
        targetExclude(
            "**/io/micronaut/core/io/scan/AnnotationClassReader.java",
            "**/io/micronaut/core/io/scan/Attribute.java",
            "**/io/micronaut/core/io/scan/Context.java",
            "**/io/micronaut/core/util/clhm/**",
            "**/io/micronaut/core/util/AntPathMatcher.java"
        )
    }
    format("javaMisc") {
        targetExclude("**/io/micronaut/core/util/clhm/**")
    }
}

val versionInfo = tasks.register<WriteProperties>("micronautVersionInfo") {
    destinationFile = layout.buildDirectory.file("resources/version/micronaut-version.properties")
    val projectVersion: String = project.properties["projectVersion"].toString()
    property("micronaut.version", projectVersion)
}

tasks {
    processResources {
        from(versionInfo)
    }
}

tasks.withType<JapicmpTask>().configureEach {
    richReport {
        addViolationTransformer(RemovedPackages::class.java, mapOf(
            "prefixes" to "io.micronaut.caffeine",
            "exact" to "")
        )
    }
}

noReflection {
    allowIn("io.micronaut.core.annotation.AnnotationMetadata", "ANNOTATIONS", "CLASS_LOADING", "ENUM_CONSTANTS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.core.annotation.AnnotationMetadataDelegate", "ANNOTATION_SYNTHESIS")
    allowIn("io.micronaut.core.annotation.AnnotationMetadataProvider", "ANNOTATION_SYNTHESIS")
    allowIn("io.micronaut.core.annotation.AnnotationSource", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.core.annotation.AnnotationValue", "CLASS_LOADING", "CLASS_MEMBERS", "ENUM_CONSTANTS", "REFLECTION_UTILS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.core.annotation.AnnotationValueResolver", "ENUM_CONSTANTS")
    allowIn("io.micronaut.core.annotation.EmptyAnnotationMetadata", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.core.beans.BeanConstructor", "CLASS_NAMES")
    allowIn("io.micronaut.core.beans.DefaultBeanIntrospectionsProvider", "SERVICE_LOADING")
    allowIn("io.micronaut.core.beans.DefaultBeanIntrospector", "CLASS_LOADING", "SERVICE_LOADING")
    allowIn("io.micronaut.core.convert.CharSequenceToEnumConverter", "ENUM_CONSTANTS")
    allowIn("io.micronaut.core.convert.ConversionContext", "ANNOTATION_SYNTHESIS")
    allowIn("io.micronaut.core.convert.DefaultMutableConversionService", "CLASS_LOADING", "INTERFACES", "REFLECTIVE_ACCESS", "SERVICE_LOADING")
    allowIn("io.micronaut.core.convert.value.ConvertibleMultiValues", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.core.convert.value.ConvertibleValues", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.core.execution.DelayedExecutionFlowImpl", "FIELD_UPDATERS")
    allowIn("io.micronaut.core.graal.GraalReflectionConfigurer", "CLASS_MEMBERS")
    allowIn("io.micronaut.core.io.buffer.ReadBuffer", "CLASS_NAMES")
    allowIn("io.micronaut.core.io.service.DefaultServiceDefinition", "CLASS_MEMBERS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils", "CLASS_LOADING", "HANDLES")
    allowIn("io.micronaut.core.io.service.ServiceLoaderFeature", "ANNOTATIONS", "CLASS_MEMBERS", "REFLECTIVE_ACCESS", "SERVICE_LOADING")
    allowIn("io.micronaut.core.io.service.SoftServiceLoader", "CLASS_LOADING", "CLASS_MEMBERS", "HANDLES", "REFLECTIVE_ACCESS", "SERVICE_LOADING")
    allowIn("io.micronaut.core.naming.conventions.MethodConvention", "ENUM_CONSTANTS")
    allowIn("io.micronaut.core.naming.conventions.TypeConvention", "CLASS_NAMES")
    allowIn("io.micronaut.core.optim.StaticOptimizations", "SERVICE_LOADING")
    allowIn("io.micronaut.core.reflect.ClassUtils", "CLASS_LOADING", "INTERFACES")
    allowIn("io.micronaut.core.reflect.GenericTypeUtils", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.core.reflect.InstantiationUtils", "CLASS_LOADING", "CLASS_MEMBERS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.core.reflect.ReflectionUtils", "CLASS_MEMBERS", "CLASS_NAMES", "INTERFACES", "REFLECTION_UTILS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.core.serialize.JdkSerializer", "CLASS_LOADING", "SERIALIZATION")
    allowIn("io.micronaut.core.type.Argument", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.core.type.DefaultArgument", "CLASS_NAMES", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.core.type.DefaultArgumentValue", "ANNOTATION_SYNTHESIS")
    allowIn("io.micronaut.core.type.RuntimeTypeInformation", "SERVICE_LOADING")
    allowIn("io.micronaut.core.type.TypeInformation", "CLASS_NAMES", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.core.util.ArrayUtils", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.core.util.CollectionUtils", "CLASS_MEMBERS", "ENUM_CONSTANTS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.core.util.CopyOnWriteMap", "HANDLES")
}
