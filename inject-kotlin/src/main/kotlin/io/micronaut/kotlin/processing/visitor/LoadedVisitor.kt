package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.micronaut.core.order.Ordered
import io.micronaut.inject.visitor.TypeElementVisitor
import org.omg.CORBA.Object

class LoadedVisitor(val visitor: TypeElementVisitor<*, *>,
                    val visitorContext: KotlinVisitorContext): Ordered {

    companion object {
        const val ANY = "kotlin.Any"
    }

    var classAnnotation: String = ANY
    var elementAnnotation: String = ANY

    init {
        val javaClass = visitor.javaClass
        val resolver = visitorContext.resolver
        val declaration = resolver.getClassDeclarationByName(javaClass.name)
        val tevClassName = TypeElementVisitor::class.java.name

        if (declaration != null) {
            val reference = declaration.superTypes
                .map { it.resolve() }
                .find {
                    it.declaration.qualifiedName?.asString() == tevClassName
                }!!

            classAnnotation = reference.arguments[0].type!!.resolve().declaration.qualifiedName!!.asString()
            if (classAnnotation == ANY) {
                classAnnotation = visitor.classType
            }

            elementAnnotation = reference.arguments[1].type!!.resolve().declaration.qualifiedName!!.asString()
            if (elementAnnotation == ANY) {
                elementAnnotation = visitor.elementType
            }
        }
        if (classAnnotation == ANY) {
            classAnnotation = Object::class.java.name
        }
        if (elementAnnotation == ANY) {
            elementAnnotation = Object::class.java.name
        }
    }

    fun matches(classDeclaration: KSClassDeclaration): Boolean {
        if (classAnnotation == "java.lang.Object") {
            return true
        }
        val annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(classDeclaration)

        return annotationMetadata.hasStereotype(classAnnotation)
    }
}
