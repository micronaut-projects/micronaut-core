package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.ExecutableMethod
import io.micronaut.scheduling.annotation.Scheduled
import spock.lang.Issue

class EnvironmentAccessExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/9622#issuecomment-1654002320')
    void "test environment access expression with placeholder expression"() {
        given:
        def ctx = buildContext("""
            package test;

            import io.micronaut.scheduling.annotation.Scheduled;

            @jakarta.inject.Singleton
            class MyJob {

                private boolean paused = false;

                @Scheduled(fixedRate = "\${hello.world.rate}", condition = "#{!this.paused}" )
                public void doSomething() {
                    System.out.println("The job runs...");
                }

                public boolean isPaused() {
                    return paused;
                }

            }
        """)

        def type = ctx.classLoader.loadClass('test.MyJob')

        ctx.environment.addPropertySource(PropertySource.of("test",
                ['hello.world.rate': '100s']))

        def bean = ctx.getBeanDefinition(type)
        def method = bean.getRequiredMethod("doSomething")

        expect:
        method.getAnnotationValuesByType(Scheduled).get(0).stringValue("fixedRate").get() == '100s'

        cleanup:
        ctx.close()
    }

    void "test environment access"() {
        given:
        def ctx = buildContext("""
            package test;

            import io.micronaut.context.annotation.Value;

            @jakarta.inject.Singleton
            class Expr {

                @Value("#{ env['first.property'] }")
                public String firstProperty;

                @Value("#{ env['second' + '.' + 'property'] }")
                public String secondProperty;

                @Value("#{ env [ 'third.property' ].toUpperCase() }")
                public String thirdProperty;

                @Value("#{ env['nullable.property']?.toUpperCase() }")
                public String nullableProperty;

            }
        """)

        def type = ctx.classLoader.loadClass('test.Expr')

        ctx.environment.addPropertySource(PropertySource.of("test",
                ['first.property': 'firstValue',
                 'second.property': 'secondValue',
                 'third.property': 'thirdValue']))

        def bean = ctx.getBean(type)

        expect:
        bean.firstProperty == 'firstValue'
        bean.secondProperty == 'secondValue'
        bean.thirdProperty == 'THIRDVALUE'
        bean.nullableProperty == null

        cleanup:
        ctx.close()
    }
}
