package io.micronaut.context

import io.micronaut.fixtures.context.MicronautApplicationTest
import io.micronaut.inject.BeanDefinitionReference

/**
 * NOTE: If you edit this test case it's likely that you also want to
 * edit its Groovy counterpart found in `inject-groovy`.
 */
class ApplicationContextCustomizerSpec extends MicronautApplicationTest {
    def "no service file is generated if application doesn't provide a customizer"() {
        javaSourceFile("Application.java", """
import io.micronaut.context.annotation.ContextConfigurer;

@ContextConfigurer
class Application {}
""")
        when:
        compile()

        then:
        serviceFiles == []
    }

    def "generates a service file"() {
        javaSourceFile("Application.java", """
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.ApplicationContextCustomizer;

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
        javaSourceFile("demo/app/Application.java", """package demo.app;

import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.ApplicationContextCustomizer;
import io.micronaut.context.ApplicationContextBuilder;
import jakarta.inject.Singleton;
import java.util.Collections;

@Singleton
class Application {
    @ContextConfigurer
    public static class Configurer implements ApplicationContextCustomizer {
        @Override
        public void customize(ApplicationContextBuilder builder) {
            builder.deduceEnvironment(false);
            builder.environments("dummy");
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

    def "reasonable error message if @ContextConfigurer is used on a type with constructor with args"() {
        javaSourceFile("demo/app/Application.java", """package demo.app;

import io.micronaut.context.annotation.ContextConfigurer;

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
        RuntimeException ex = thrown()
        ex.cause.message == 'demo.app.Application is annotated with @ContextConfigurer but has at least one constructor with arguments, which isn\'t supported.'
    }
}
