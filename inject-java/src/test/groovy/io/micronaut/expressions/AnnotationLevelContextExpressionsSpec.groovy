package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec

class AnnotationLevelContextExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test annotation level context"() {
        given:
        Object expr = buildSingleExpressionFromClass("test.Expr", """

            package test;
            import io.micronaut.context.annotation.EvaluatedExpressionContext;import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

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

            @EvaluatedExpressionContext(CustomContext.class)
            @interface CustomAnnotation {
                String value();
            }

        """).evaluate()

        expect:
        expr instanceof String && expr == "annotationLevelValue"

    }

    void "test annotation member level context"() {
        given:
        Object expr = buildSingleExpressionFromClass("test.Expr", """

            package test;
            import io.micronaut.context.annotation.EvaluatedExpressionContext;import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

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
                @EvaluatedExpressionContext(CustomContext.class)
                String customValue();
            }

        """).evaluate()

        expect:
        expr instanceof String && expr == "annotationLevelValue"

    }

}
