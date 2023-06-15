package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ElementQuery

class NullabilityFutureAnnotationsSpec extends AbstractTypeElementSpec {

    void "test map nonnull on type arguments"() {
        given:
        def element = buildClassElement("""
package test;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import java.util.concurrent.CompletionStage;

@jakarta.inject.Singleton
class Test {
    CompletionStage<@NonNull String> notNullableMethod(String test) {
        return null;
    }
    CompletionStage<@Nullable String> nullableMethod(String test) {
        return null;
    }
    CompletionStage<String> method(String test) {
        return null;
    }
}
""")
        when:
        def notNullableMethod = element.getEnclosedElement(ElementQuery.ALL_METHODS.named({ String st -> st == 'notNullableMethod' })).get()

        then:
        notNullableMethod.getReturnType().getFirstTypeArgument().get().isNonNull()
        !notNullableMethod.getReturnType().getFirstTypeArgument().get().isNullable()

        when:
        def nullableMethod = element.getEnclosedElement(ElementQuery.ALL_METHODS.named({ String st -> st == 'nullableMethod' })).get()

        then:
        !nullableMethod.getReturnType().getFirstTypeArgument().get().isNonNull()
        nullableMethod.getReturnType().getFirstTypeArgument().get().isNullable()

        when:
        def method = element.getEnclosedElement(ElementQuery.ALL_METHODS.named({ String st -> st == 'method' })).get()

        then:
        !method.getReturnType().getFirstTypeArgument().get().isNonNull()
        !method.getReturnType().getFirstTypeArgument().get().isNullable()
    }
}
