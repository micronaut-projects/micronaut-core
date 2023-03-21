package io.micronaut.docs.expressions

import io.micronaut.context.annotation.AnnotationExpressionContext
import jakarta.inject.Singleton

@Singleton
@CustomAnnotation(value = "#{firstValue() + secondValue()}") // <1>
class Example

@Singleton
class AnnotationContext { // <2>
    fun firstValue(): String {
        return "first value"
    }
}

@Singleton
class AnnotationMemberContext { // <3>
    fun secondValue(): String {
        return "second value"
    }
}

@AnnotationExpressionContext(AnnotationContext::class) // <4>
annotation class CustomAnnotation(
    @get:AnnotationExpressionContext(AnnotationMemberContext::class) // <5>
    val value: String
)
