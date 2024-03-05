package io.micronaut.context

import io.micronaut.fixtures.context.MicronautApplicationTest
import io.micronaut.inject.BeanDefinitionReference
import org.codehaus.groovy.GroovyBugError

class ApplicationContextConfigurerSpec extends MicronautApplicationTest {
    def "no service file is generated if application doesn't provide a configurer"() {
        groovySourceFile("Application.groovy", """
import io.micronaut.context.annotation.ContextConfigurer

@ContextConfigurer
class Application {}
""")
        when:
        compile()

        then:
        serviceFiles == []
    }

    def "generates a service file"() {
        groovySourceFile("Application.groovy", """
import io.micronaut.context.annotation.ContextConfigurer
import io.micronaut.context.ApplicationContextConfigurer

@ContextConfigurer
class Application implements ApplicationContextConfigurer {}
""")
        when:
        compile()

        then:
        hasServiceFileFor(ApplicationContextConfigurer) {
            withImplementations 'Application'
        }
    }

    def "can configure application via an inner customizer"() {
        groovySourceFile("demo/app/Application.groovy", """package demo.app;

import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.ApplicationContextBuilder;
import jakarta.inject.Singleton;
import java.util.Collections;

@Singleton
class Application {
    @ContextConfigurer
    static class Configurer implements ApplicationContextConfigurer {
        @Override
        void configure(ApplicationContextBuilder builder) {
            builder.deduceEnvironment(false)
            builder.environments("dummy")
        }
    }
}
""")
        when:
        compile()

        then:
        hasServiceFileFor(ApplicationContextConfigurer) {
            withImplementations 'demo.app.Application$Configurer'
        }
        hasServiceFileFor(BeanDefinitionReference) {
            withImplementations 'demo.app.$Application$Definition'
        }

        when:
        def ctx = loadContext "demo.app.Application"

        then:
        ctx.environment.activeNames == ['dummy'] as Set<String>
    }

    def "reasonable error message if @ContextConfigurer is used on a type with constructor with args"() {
        groovySourceFile("demo/app/Application.groovy", """package demo.app

import io.micronaut.context.annotation.ContextConfigurer

@ContextConfigurer
class Application {
    public Application(String param) {
        // should fail
    }
}
""")
        when:
        compile()

        then:
        GroovyBugError ex = thrown()
        ex.cause.message == 'demo.app.Application is annotated with @ContextConfigurer but has at least one constructor with arguments, which isn\'t supported. To resolve this create a separate class with no constructor arguments annotated with @ContextConfigurer, which sole role is configuring the application context.'
    }
}
