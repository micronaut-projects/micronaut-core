package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.NoSuchBeanException

class TestExpressionsInjectionSpec extends AbstractEvaluatedExpressionsSpec {

    void "test expression field injection"() {
        given:
        def ctx = buildContext("""
            package test;

            import jakarta.inject.Singleton;
            import io.micronaut.context.annotation.Value;

            @Singleton
            class Expr {
                @Value("#{ 15 ^ 2 }")
                private int intValue;

                @Value("#{ 100 }")
                protected Integer boxedIntValue;

                @Value("#{ T(String).join(',', 'a', 'b', 'c') }")
                public String strValue;

                @Value("#{null}")
                private Object nullValue;
            }
        """)

        def type = ctx.classLoader.loadClass('test.Expr')
        def bean = ctx.getBean(type)

        expect:
        bean.intValue == 225
        bean.boxedIntValue == 100
        bean.strValue == 'a,b,c'
        bean.nullValue == null

        cleanup:
        ctx.close()
    }

    void "test expression constructor injection"() {
        given:
        def ctx = buildContext("""
            package test;

            import jakarta.inject.Singleton;
            import io.micronaut.context.annotation.Value;

            @Singleton
            class Expr {
                private final Integer wrapper;
                private final Integer primitive;

                public Expr(@Value("#{ 25 }") Integer wrapper,
                            @Value("#{ 23 }") int primitive) {
                    this.wrapper = wrapper;
                    this.primitive = primitive;
                }
            }
        """)

        def type = ctx.classLoader.loadClass('test.Expr')
        def bean = ctx.getBean(type)

        expect:
        bean.wrapper == 25
        bean.primitive == 23

        cleanup:
        ctx.close()
    }

    void "test expression setter injection"() {
        given:
        def ctx = buildContext("""
            package test;

            import jakarta.inject.Inject;
            import jakarta.inject.Singleton;
            import io.micronaut.context.annotation.Value;

            @Singleton
            class Expr {
                private Integer wrapper;
                private int primitive;

                @Inject
                public void setWrapper(@Value("#{ 25 }") Integer value) {
                    this.wrapper = value;
                }

                @Inject
                public void setPrimitive(@Value("#{ 23 }") int value) {
                    this.primitive = value;
                }
            }
        """)

        def type = ctx.classLoader.loadClass('test.Expr')
        def bean = ctx.getBean(type)

        expect:
        bean.wrapper == 25
        bean.primitive == 23

        cleanup:
        ctx.close()
    }

    void "test expressions in @Factory injection"() {
        given:
        def ctx = buildContext("""
            package test;

            import io.micronaut.context.annotation.Bean;
            import io.micronaut.context.annotation.Factory;
            import jakarta.inject.Inject;
            import jakarta.inject.Singleton;
            import io.micronaut.context.annotation.Value;

            @jakarta.inject.Singleton
            class Context {
                public String getContextValue() {
                    return "context value";
                }
            }

            class Expr {
                private final Integer wrapper;
                private final int primitive;
                private final String contextValue;

                Expr(Integer wrapper, int primitive, String contextValue) {
                    this.wrapper = wrapper;
                    this.primitive = primitive;
                    this.contextValue = contextValue;
                }
            }

            @Factory
            class TestFactory {
                @Bean
                public Expr factoryBean(@Value("#{ 25 }") Integer wrapper,
                                        @Value("#{ 23 }") int primitive,
                                        @Value("#{ #contextValue }") String contextValue) {
                    return new Expr(wrapper, primitive, contextValue);
                }
            }
        """)

        def type = ctx.classLoader.loadClass('test.Expr')
        def bean = ctx.getBean(type)

        expect:
        bean.wrapper == 25
        bean.primitive == 23
        bean.contextValue == "context value"

        cleanup:
        ctx.close()
    }
}
