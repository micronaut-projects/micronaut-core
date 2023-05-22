package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.aop.InterceptorBinding
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.NamedAnnotationMapper
import io.micronaut.inject.visitor.VisitorContext

class NamedTestAnnMapper: NamedAnnotationMapper {

    override fun map(
        annotation: AnnotationValue<Annotation>,
        visitorContext: VisitorContext
    ): List<AnnotationValue<*>> {
        return listOf(AnnotationValue.builder(InterceptorBinding::class.java)
            .value(name)
            .build())
    }

    override fun getName(): String {
        return "mapperbinding.TestAnn"
    }
}
