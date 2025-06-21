package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.NamedAnnotationTransformer
import io.micronaut.inject.visitor.VisitorContext

class TestStereotypeAnnTransformer: NamedAnnotationTransformer {

    override fun transform(
        annotation: AnnotationValue<Annotation>,
        visitorContext: VisitorContext
    ): List<AnnotationValue<*>> {
        return listOf(AnnotationValue.builder(InterceptorBinding::class.java)
            .member("kind", InterceptorKind.AROUND)
            .member("bindMembers", true)
            .build())
    }

    override fun getName(): String {
        return "mapperbindingmembers.MyInterceptorBinding"
    }
}
