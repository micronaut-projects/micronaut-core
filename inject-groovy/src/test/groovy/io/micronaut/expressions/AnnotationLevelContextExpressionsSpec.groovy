package io.micronaut.expressions

import io.micronaut.ast.transform.test.AbstractEvaluatedExpressionsSpec


class AnnotationLevelContextExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test annotation level context"() {
        given:
        Object result = evaluateSingle("test.Expr", """
            package test

            import io.micronaut.context.annotation.AnnotationExpressionContext
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton

            @Singleton
            @CustomAnnotation("#{ #getAnnotationLevelValue() }")
            class Expr {
            }

            @Singleton
            class CustomContext {
                String getAnnotationLevelValue() {
                    return "annotationLevelValue";
                }
            }

            @AnnotationExpressionContext(CustomContext.class)
            @interface CustomAnnotation {
                String value();
            }

        """)

        expect:
        result instanceof String && result == "annotationLevelValue"

    }

    void "test annotation member level context"() {
        given:
        Object result = evaluateSingle("test.Expr", """
            package test

            import io.micronaut.context.annotation.AnnotationExpressionContext
            import io.micronaut.context.annotation.Executable
            import io.micronaut.context.annotation.Requires
            import jakarta.inject.Singleton

            @Singleton
            @CustomAnnotation(customValue = "#{ #getAnnotationLevelValue() }")
            class Expr {
            }

            @Singleton
            class CustomContext {
                String getAnnotationLevelValue() {
                    return "annotationLevelValue";
                }
            }

            @interface CustomAnnotation {
                @AnnotationExpressionContext(CustomContext.class)
                String customValue();
            }

        """)

        expect:
        result instanceof String && result == "annotationLevelValue"

    }

}
