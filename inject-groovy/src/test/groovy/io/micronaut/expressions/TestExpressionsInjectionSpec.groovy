package io.micronaut.expressions

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.NoSuchBeanException

class TestExpressionsInjectionSpec extends AbstractBeanDefinitionSpec {

    void "test expression field injection"() {
        given:
        def ctx = buildContext("""
            package test

            import jakarta.inject.Singleton
            import io.micronaut.context.annotation.Value

            @Singleton
            class Expr {
                @Value("#{ 15 ^ 2 }")
                int intValue

                @Value("#{ 100 }")
                Integer boxedIntValue

                @Value("#{ T(String).join(',', 'a', 'b', 'c') }")
                String strValue

                @Value("#{null}")
                Object nullValue
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
            package test

            import jakarta.inject.Singleton
            import io.micronaut.context.annotation.Value

            @Singleton
            class Expr {
                private Integer wrapper;
                private int primitive;

                Expr(@Value("#{ 25 }") Integer wrapper,
                     @Value("#{ 23 }") int primitive) {
                    this.wrapper = wrapper
                    this.primitive = primitive
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
            package test

            import jakarta.inject.Inject;
            import jakarta.inject.Singleton
            import io.micronaut.context.annotation.Value

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
            package test

            import io.micronaut.context.annotation.Bean
            import io.micronaut.context.annotation.Factory
            import jakarta.inject.Inject
            import jakarta.inject.Singleton
            import io.micronaut.context.annotation.Value

            @jakarta.inject.Singleton
            class Context {
                String contextValue = "context value"
            }

            class Expr {
                private final Integer wrapper
                private final int primitive
                private final String contextValue

                Expr(Integer wrapper, int primitive, String contextValue) {
                    this.wrapper = wrapper
                    this.primitive = primitive
                    this.contextValue = contextValue
                }
            }

            @Factory
            class TestFactory {
                @Bean
                Expr factoryBean(@Value('#{ 25 }') Integer wrapper,
                                 @Value('#{ 23 }') int primitive,
                                 @Value('#{ #contextValue }') String contextValue) {
                    return new Expr(wrapper, primitive, contextValue)
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
