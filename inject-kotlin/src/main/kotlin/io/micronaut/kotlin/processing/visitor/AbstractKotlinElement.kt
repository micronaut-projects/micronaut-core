package io.micronaut.kotlin.processing.visitor;

import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.Modifier
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.ast.Element
import java.util.function.Consumer
import java.util.function.Predicate

abstract class AbstractKotlinElement(private val declaration: KSDeclaration) : Element {

    override fun getName(): String {
        return declaration.simpleName.asString()
    }

    override fun getNativeType(): Any {
        return declaration
    }

    override fun isProtected(): Boolean {
        return declaration.modifiers.contains(Modifier.PROTECTED)
    }

    override fun isPublic(): Boolean {
        return declaration.modifiers.contains(Modifier.PUBLIC)
    }

    override fun <T : Annotation?> annotate(
        annotationType: String,
        consumer: Consumer<AnnotationValueBuilder<T>>
    ): Element {
        return super.annotate(annotationType, consumer)
    }

    override fun removeAnnotation(annotationType: String): Element {
        return super.removeAnnotation(annotationType)
    }

    override fun <T : Annotation?> removeAnnotationIf(predicate: Predicate<AnnotationValue<T>>): Element {
        return super.removeAnnotationIf(predicate)
    }

}
