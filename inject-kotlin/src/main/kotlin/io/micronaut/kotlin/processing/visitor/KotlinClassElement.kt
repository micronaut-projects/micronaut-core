package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import io.micronaut.inject.ast.ArrayableClassElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.ElementQuery
import java.util.*

class KotlinClassElement(private val classDeclaration: KSClassDeclaration,
                         private val visitorContext: KotlinVisitorContext,
                         private val arrayDimensions: Int = 0): ArrayableClassElement {

    override fun getName(): String {
        return classDeclaration.qualifiedName!!.asString()
    }

    override fun isProtected(): Boolean {
        return classDeclaration.modifiers.contains(Modifier.PROTECTED)
    }

    override fun isPublic(): Boolean {
        return classDeclaration.modifiers.contains(Modifier.PUBLIC)
    }

    override fun getNativeType(): Any {
        return classDeclaration
    }

    override fun isAssignable(type: String?): Boolean {
        TODO("Not yet implemented")
    }

    override fun isArray(): Boolean {
        return arrayDimensions > 0
    }

    override fun getArrayDimensions(): Int {
        return arrayDimensions
    }

    override fun withArrayDimensions(arrayDimensions: Int): ClassElement {
        return KotlinClassElement(classDeclaration, visitorContext, arrayDimensions);
    }

    override fun isInner(): Boolean {
        return classDeclaration.parentDeclaration is KSClassDeclaration
    }

    override fun getEnclosingType(): Optional<ClassElement> {
        if (isInner) {
            val parentDeclaration = classDeclaration.parentDeclaration as KSClassDeclaration
            return Optional.of(
                visitorContext.elementFactory.newClassElement(
                    parentDeclaration,
                    visitorContext.getAnnotationUtils().getAnnotationMetadata(parentDeclaration)
                )
            )
        }
        return Optional.empty()
    }

    override fun <T : Element?> getEnclosedElements(query: ElementQuery<T>): MutableList<T> {
        return super.getEnclosedElements(query)
    }
}
