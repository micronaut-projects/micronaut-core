package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec

class ClassMethodRefExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test evaluating method reference defined on the class"() {
        given:
            def ctx = buildContext('tst.MyBean', """

            package tst;

            import io.micronaut.context.annotation.Executable;
            import jakarta.inject.Singleton;
            import io.micronaut.aop.Around;
            import io.micronaut.context.annotation.Type;
            import java.lang.annotation.*;

            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            import io.micronaut.aop.MethodInterceptor;
            import io.micronaut.aop.MethodInvocationContext;

            @Singleton
            class ProxyAroundInterceptor implements MethodInterceptor<Object, Object> {

                @Override
                public Object intercept(MethodInvocationContext<Object, Object> context) {
                    return context.proceed() + " " + context.stringValue("tst.CustomAnnotation").orElseThrow();
                }
            }

            @Around
            @Type(ProxyAroundInterceptor.class)
            @Documented
            @Retention(RUNTIME)
            @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
            @interface ProxyAround {
            }

            @Singleton
            @ProxyAround
            @CustomAnnotation("#{ this.getNumber() }")
            class MyBean {

                @Executable
                int getNumber() {
                    return 123;
                }

                @Executable
                String callMe() {
                    return "Abc";
                }

            }

            @interface CustomAnnotation {
                String value();
            }

        """)


            def bean = ctx.getBean(ctx.getClassLoader().loadClass("tst.MyBean"))
        expect:
            bean.callMe() == "Abc 123"

        cleanup:
            ctx.stop()
    }

}
