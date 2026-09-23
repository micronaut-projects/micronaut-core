package io.micronaut.graal.reflect

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.ReflectionConfig
import io.micronaut.core.graal.GraalReflectionConfigurer
import io.micronaut.core.naming.NameUtils

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

class GraalTypeElementVisitorKotlinSpec extends AbstractKotlinCompilerSpec {

    void "test write reflect config for a @ReflectiveAccess suspend function"() {
        given:
        ClassLoader classLoader = buildClassLoader('test.Test', '''
package test

import io.micronaut.core.annotation.ReflectiveAccess

class Test {

    @ReflectiveAccess
    suspend fun suspending(name: String): String = name

    @ReflectiveAccess
    suspend fun suspendingUnit(name: String) {
    }

    @ReflectiveAccess
    fun blocking(name: String): String = name
}
''')
        GraalReflectionConfigurer configurer = reflectionConfigurer(classLoader, 'test.Test')

        when:
        AnnotationValue<ReflectionConfig> config = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig).first()
        Map<String, List<String>> methods = config.getAnnotations("methods").collectEntries {
            [(it.stringValue("name").get()): it.stringValues("parameterTypes").toList()]
        }

        then:
        config.stringValue("type").get() == 'test.Test'
        methods == [
                suspending    : ['java.lang.String', 'kotlin.coroutines.Continuation'],
                suspendingUnit: ['java.lang.String', 'kotlin.coroutines.Continuation'],
                blocking      : ['java.lang.String']
        ]

        when:
        Class<?> type = classLoader.loadClass('test.Test')
        List<Method> registered = registeredMethods(configurer, classLoader)

        then:
        registered.toSet() == [
                type.getDeclaredMethod('suspending', String, classLoader.loadClass('kotlin.coroutines.Continuation')),
                type.getDeclaredMethod('suspendingUnit', String, classLoader.loadClass('kotlin.coroutines.Continuation')),
                type.getDeclaredMethod('blocking', String)
        ].toSet()
    }

    void "test write reflect config for a private @Executable suspend function of a bean"() {
        given:
        ClassLoader classLoader = buildClassLoader('test.Test', '''
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.ReflectiveAccess
import jakarta.inject.Singleton

@Singleton
open class Test {

    @Executable
    @ReflectiveAccess
    private suspend fun suspending(name: String): String = name
}
''')
        GraalReflectionConfigurer configurer = reflectionConfigurer(classLoader, 'test.Test')
        Class<?> type = classLoader.loadClass('test.Test')

        expect:
        registeredMethods(configurer, classLoader).toSet() == [
                type.getDeclaredMethod('suspending', String, classLoader.loadClass('kotlin.coroutines.Continuation'))
        ].toSet()
    }

    private static GraalReflectionConfigurer reflectionConfigurer(ClassLoader classLoader, String className) {
        String configurerName = NameUtils.getPackageName(className) + '.$' + NameUtils.getSimpleName(className) + GraalReflectionConfigurer.CLASS_SUFFIX
        return (GraalReflectionConfigurer) classLoader.loadClass(configurerName).getDeclaredConstructor().newInstance()
    }

    /**
     * Runs the configurer the way the native image feature does, which silently skips a method
     * whose parameter types don't match a declared method.
     */
    private static List<Method> registeredMethods(GraalReflectionConfigurer configurer, ClassLoader classLoader) {
        List<Method> methods = []
        configurer.configure(new GraalReflectionConfigurer.ReflectionConfigurationContext() {
            @Override
            Class<?> findClassByName(String name) {
                try {
                    return Class.forName(name, false, classLoader)
                } catch (ClassNotFoundException ignored) {
                    return null
                }
            }

            @Override
            void register(Class<?>... types) {
                // only the registered methods are of interest here
            }

            @Override
            void register(Method... ms) {
                methods.addAll(ms)
            }

            @Override
            void register(Field... fields) {
                // only the registered methods are of interest here
            }

            @Override
            void register(Constructor<?>... constructors) {
                // only the registered methods are of interest here
            }

            @Override
            void registerDynamicProxy(Class<?>... interfaces) {
                // only the registered methods are of interest here
            }
        })
        return methods
    }
}
