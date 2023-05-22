package io.micronaut.docs.expressions;

import jakarta.inject.Singleton;
import io.micronaut.context.annotation.AnnotationExpressionContext;

@Singleton
@CustomAnnotation(value = "#{firstValue() + secondValue()}") // <1>
class Example {
}

@Singleton
class AnnotationContext { // <2>
    String firstValue() {
        return "first value";
    }
}

@Singleton
class AnnotationMemberContext { // <3>
    String secondValue() {
        return "second value";
    }
}

@AnnotationExpressionContext(AnnotationContext.class) // <4>
@interface CustomAnnotation {

    @AnnotationExpressionContext(AnnotationMemberContext.class) // <5>
    String value();
}
