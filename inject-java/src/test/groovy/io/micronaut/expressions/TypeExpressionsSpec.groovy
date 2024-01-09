package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.PrimitiveElement

class TypeExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test type of class annotation"() {
        given:
        Map<String, ClassElement> types =  ExpressionTypeCollector.TYPES
        evaluateSingle("test.Expr", """

            package exp;
            import io.micronaut.context.annotation.AnnotationExpressionContext;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            @ExpAnnotation("#{ 1 }")
            class Expr1 {
            }

            @Singleton
            @ExpAnnotation("#{ 'str' }")
            class Expr2 {
            }

            @interface ExpAnnotation {
                String value();
            }

        """)

        expect:
        types['#{ 1 }'] == PrimitiveElement.INT
        types['''#{ 'str' }'''].name == "java.lang.String"

        cleanup:
        types.clear()
    }

    void "test type of method annotation"() {
        given:
        Map<String, ClassElement> types =  ExpressionTypeCollector.TYPES
        evaluateSingle("test.Expr", """

            package exp;
            import io.micronaut.context.annotation.AnnotationExpressionContext;
            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            class Expr1 {
                @ExpAnnotation("#{ 1 }")
                void myMethod() {
                }

            }

            @Singleton
            class Expr2 {
                @ExpAnnotation("#{ 'strX' }")
                void myMethod() {
                }
            }

            @interface ExpAnnotation {
                String value();
            }

        """)

        expect:
        types['#{ 1 }'] == PrimitiveElement.INT
        types['''#{ 'strX' }'''].name == "java.lang.String"

        cleanup:
        types.clear()
    }

}
