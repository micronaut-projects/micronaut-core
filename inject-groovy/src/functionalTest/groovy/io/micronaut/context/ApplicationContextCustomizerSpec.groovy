package io.micronaut.context

import io.micronaut.fixtures.context.MicronautApplicationTest
import io.micronaut.inject.BeanDefinitionReference

class ApplicationContextCustomizerSpec extends MicronautApplicationTest {
    def "no service file is generated if application doesn't provide a customizer"() {
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
import io.micronaut.context.ApplicationContextCustomizer

@ContextConfigurer
class Application implements ApplicationContextCustomizer {}
""")
        when:
        compile()

        then:
        hasServiceFileFor(ApplicationContextCustomizer) {
            withImplementations 'Application'
        }
    }

    def "can configure application via an inner customizer"() {
        groovySourceFile("demo/app/Application.groovy", """package demo.app;

import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.ApplicationContextCustomizer;
import io.micronaut.context.ApplicationContextBuilder;
import jakarta.inject.Singleton;
import java.util.Collections;

@Singleton
class Application {
    @ContextConfigurer
    static class Configurer implements ApplicationContextCustomizer {
        @Override
        void customize(ApplicationContextBuilder builder) {
            builder.deduceEnvironment(false)
            builder.environments("dummy")
        }
    }
}
""")
        when:
        compile()

        then:
        hasServiceFileFor(ApplicationContextCustomizer) {
            withImplementations 'demo.app.Application$Configurer'
        }
        hasServiceFileFor(BeanDefinitionReference) {
            withImplementations 'demo.app.$Application$Definition$Reference'
        }

        when:
        def ctx = loadContext "demo.app.Application"

        then:
        ctx.environment.activeNames == ['dummy'] as Set<String>
    }
}
