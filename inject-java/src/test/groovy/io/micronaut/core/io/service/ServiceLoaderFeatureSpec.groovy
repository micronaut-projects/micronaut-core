package io.micronaut.core.io.service

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.expressions.EvaluatedExpression
import io.micronaut.core.graal.GraalReflectionConfigurer
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import org.graalvm.nativeimage.hosted.Feature

import java.lang.reflect.Field
import java.lang.reflect.Method

class ServiceLoaderFeatureSpec extends AbstractTypeElementSpec {

    void "test expressions are registered for build time init"() {
        when:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import jakarta.inject.Singleton;

@Singleton
@Requires(value = "#{ '123' + 'abc' }")
@Requires(condition = CustomCondition.class)
class Test {

    @Executable
    @Requires(value = "#{ '456' + 'abc' }")
    void test(String first, String second) {
    }
}

class CustomCondition implements Condition {
    @Override public boolean matches(ConditionContext context) {
        return false;
    }
}
''')
        then:
        definition != null

        when:
        Set<Class<?>> buildTimeInitialized = []
        ServiceLoaderFeature serviceLoaderFeature = new ServiceLoaderFeature() {
            @Override
            protected void initializeAtBuildTime(@Nullable Class<?> buildInitClass) {
                buildTimeInitialized.add(buildInitClass)
            }

            @Override
            protected void addImageSingleton(ServiceScanner.StaticServiceDefinitions staticServiceDefinitions) {
                // no-op
            }

            @Override
            protected void registerForReflectiveInstantiation(Class<?> c) {
                // no-op
            }

            @Override
            protected void registerRuntimeReflection(Class<?> c) {
                // no-op
            }

            @Override
            protected void registerRuntimeReflection(Method... methods) {
                // no-op
            }

            @Override
            protected void registerRuntimeReflection(Field... fields) {
                // no-op
            }

            @Override
            protected Collection<GraalReflectionConfigurer> loadReflectionConfigurers(Feature.BeforeAnalysisAccess access) {
                return []
            }

            @Override
            protected ServiceScanner.StaticServiceDefinitions buildStaticServiceDefinitions(Feature.BeforeAnalysisAccess access) {
                return new ServiceScanner.StaticServiceDefinitions((BeanDefinitionReference.name): [definition.getClass().name] as Set)
            }
        }

        def mockAccess = Mock(Feature.BeforeAnalysisAccess)
        mockAccess.findClassByName(_) >> { String typeName -> definition.getClass().classLoader.loadClass(typeName) }
        serviceLoaderFeature.beforeAnalysis(mockAccess)

        then:
        !buildTimeInitialized.isEmpty()
        buildTimeInitialized.size() == 5
        buildTimeInitialized.any { EvaluatedExpression.isAssignableFrom(it)}
    }
}
