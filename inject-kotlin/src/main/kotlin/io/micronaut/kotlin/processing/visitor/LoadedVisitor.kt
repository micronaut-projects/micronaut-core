package io.micronaut.kotlin.processing.visitor

import io.micronaut.core.order.Ordered
import io.micronaut.inject.visitor.TypeElementVisitor

class LoadedVisitor(val visitor: TypeElementVisitor<*, *>,
                    val visitorContext: KotlinVisitorContext): Ordered {

    init {
        visitor.javaClass
    }
}
