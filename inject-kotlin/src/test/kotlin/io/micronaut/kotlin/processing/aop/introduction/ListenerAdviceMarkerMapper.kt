package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.TypedAnnotationMapper
import io.micronaut.inject.visitor.VisitorContext
import java.util.ArrayList

class ListenerAdviceMarkerMapper : TypedAnnotationMapper<ListenerAdviceMarker> {

    override fun annotationType(): Class<ListenerAdviceMarker> {
        return ListenerAdviceMarker::class.java
    }

    override fun map(
        annotation: AnnotationValue<ListenerAdviceMarker>,
        visitorContext: VisitorContext
    ): List<AnnotationValue<*>> {
        val mappedAnnotations: MutableList<AnnotationValue<*>> = ArrayList()
        mappedAnnotations.add(
            AnnotationValue.builder(
                ListenerAdvice::class.java
            ).build()
        )
        return mappedAnnotations
    }
}
