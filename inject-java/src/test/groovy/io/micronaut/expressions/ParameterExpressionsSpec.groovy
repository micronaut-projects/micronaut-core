package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec
import io.micronaut.context.annotation.Value
import io.micronaut.inject.annotation.EvaluatedAnnotationValue

class ParameterExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test evaluating method parameter expression"() {
        given:
            def ctx = buildContext('tst.MyBean', """

            package tst;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Value;
            import jakarta.inject.Inject;
            import jakarta.inject.Singleton;

            @Singleton
            class MyBean {
                String someParam;
                @Executable
                void doStuff(@Value("#{1 + 1 + this.myNumber()}") String someParam) {
                    this.someParam = someParam;
                }

                int myNumber() {
                    return 5;
                }
            }

        """)


            def bean = ctx.getBean(ctx.getClassLoader().loadClass("tst.MyBean"))
            def beanDefinition = ctx.getBeanDefinition(ctx.getClassLoader().loadClass("tst.MyBean"))
            def av = beanDefinition.getRequiredMethod("doStuff", String.class).getArguments()[0].getAnnotationMetadata().getAnnotation(Value.class)
        expect:
            (av as EvaluatedAnnotationValue).withArguments(bean, "MyValue").stringValue().get() == "7"

        cleanup:
            ctx.stop()
    }

}
