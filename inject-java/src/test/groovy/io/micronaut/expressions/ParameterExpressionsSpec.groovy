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
        when:
            def av = beanDefinition.getRequiredMethod("doStuff", String.class).getArguments()[0].getAnnotationMetadata().getAnnotation(Value.class)
        then:
            (av as EvaluatedAnnotationValue).withArguments(bean, "MyValue").stringValue().get() == "7"
        when:
            buildContext('tst.MyBean', """

            package tst;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Value;
            import jakarta.inject.Inject;
            import jakarta.inject.Singleton;

            @Singleton
            class MyBean {

                @Executable
                static void doStuffStatic(@Value("#{1 + 1 + this.myNumber()}") String someParam) {
                }

                int myNumber() {
                    return 5;
                }
            }

        """)
        then:
            def e = thrown(Exception)
            e.message.contains("Cannot reference 'this'")
        when:
            buildContext('tst.MyBean2', """

            package tst;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Value;
            import jakarta.inject.Inject;
            import jakarta.inject.Singleton;

            @Singleton
            class MyBean2 {
                String someParam;
                MyBean2(@Value("#{1 + 1 + this.myNumber()}") String someParam) {
                    this.someParam = someParam;
                }

            }

        """)
        then:
            def ee = thrown(Exception)
            ee.message.contains("Cannot reference 'this'")

        cleanup:
            ctx.stop()
    }

}
