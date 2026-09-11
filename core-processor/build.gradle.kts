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
    implementation(mnSourcegen.asm)

    compileOnly(libs.managed.kotlin.stdlib.jdk8)
}


noReflection {
    allowIn("io.micronaut.aop.writer.AopProxyWriter", "REFLECTION_UTILS")
    allowIn("io.micronaut.aop.writer.RuntimeProxyBeanDefinitionWriter", "REFLECTION_UTILS")
    allowIn("io.micronaut.context.visitor.BeanImportVisitor", "SERVICE_LOADING")
    allowIn("io.micronaut.context.visitor.ConfigurationMetadataWriterVisitor", "SERVICE_LOADING")
    allowIn("io.micronaut.expressions.EvaluatedExpressionWriter", "REFLECTION_UTILS")
    allowIn("io.micronaut.expressions.parser.ast.access.BeanContextAccess", "REFLECTION_UTILS")
    allowIn("io.micronaut.expressions.parser.ast.access.ContextMethodCall", "REFLECTION_UTILS")
    allowIn("io.micronaut.expressions.parser.ast.access.ContextMethodParameterAccess", "REFLECTION_UTILS")
    allowIn("io.micronaut.expressions.parser.ast.access.ElementMethodCall", "REFLECTION_UTILS")
    allowIn("io.micronaut.expressions.parser.ast.access.EnvironmentAccess", "REFLECTION_UTILS")
    allowIn("io.micronaut.expressions.parser.ast.access.SubscriptOperator", "REFLECTION_UTILS")
    allowIn("io.micronaut.expressions.parser.ast.access.ThisAccess", "REFLECTION_UTILS")
    allowIn("io.micronaut.expressions.parser.ast.conditional.TernaryExpression", "REFLECTION_UTILS")
    allowIn("io.micronaut.expressions.parser.ast.operator.binary.ComparablesComparisonOperation", "REFLECTION_UTILS")
    allowIn("io.micronaut.expressions.parser.ast.operator.binary.MatchesOperator", "REFLECTION_UTILS")
    allowIn("io.micronaut.expressions.parser.ast.operator.binary.PowOperator", "REFLECTION_UTILS")
    allowIn("io.micronaut.expressions.parser.ast.operator.unary.EmptyOperator", "REFLECTION_UTILS")
    allowIn("io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder", "SERVICE_LOADING")
    allowIn("io.micronaut.inject.annotation.AnnotationMetadataGenUtils", "REFLECTION_UTILS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.inject.annotation.AnnotationMetadataWriter", "REFLECTION_UTILS")
    allowIn("io.micronaut.inject.annotation.internal.InterceptorBindingMembers", "ENUM_CONSTANTS")
    allowIn("io.micronaut.inject.ast.ClassElement", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.inject.ast.PropertyElementQuery", "ENUM_CONSTANTS")
    allowIn("io.micronaut.inject.ast.ReflectClassElement", "GENERIC_SIGNATURES", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.inject.ast.ReflectGenericPlaceholderElement", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.inject.ast.ReflectTypeElement", "GENERIC_SIGNATURES", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.inject.ast.ReflectWildcardElement", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.inject.ast.utils.AstBeanPropertiesUtils", "ENUM_CONSTANTS")
    allowIn("io.micronaut.inject.beans.visitor.BeanIntrospectionWriter", "CLASS_MEMBERS", "REFLECTION_UTILS")
    allowIn("io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor", "CLASS_NAMES")
    allowIn("io.micronaut.inject.processing.DeclaredBeanElementCreator", "CLASS_NAMES")
    allowIn("io.micronaut.inject.processing.JavaModelUtils", "REFLECTION_UTILS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.inject.visitor.BeanElementVisitor", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.inject.visitor.BeanElementVisitorLoader", "SERVICE_LOADING")
    allowIn("io.micronaut.inject.visitor.PackageElementVisitor", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.inject.visitor.TypeElementVisitor", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.inject.writer.ArgumentExpUtils", "REFLECTION_UTILS")
    allowIn("io.micronaut.inject.writer.BeanDefinitionWriter", "CLASS_MEMBERS", "REFLECTION_UTILS")
    allowIn("io.micronaut.inject.writer.ByteCodeWriterUtils", "CLASS_LOADING")
    allowIn("io.micronaut.inject.writer.DispatchWriter", "REFLECTION_UTILS")
    allowIn("io.micronaut.inject.writer.ExecutableMethodsDefinitionWriter", "REFLECTION_UTILS")
    allowIn("io.micronaut.inject.writer.MethodGenUtils", "REFLECTION_UTILS")
    allowIn("io.micronaut.inject.writer.ParameterDefaultValueProviderLoader", "SERVICE_LOADING")
}
