package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.micronaut.core.order.Ordered
import io.micronaut.inject.visitor.TypeElementVisitor

class LoadedVisitor(val visitor: TypeElementVisitor<*, *>,
                    val visitorContext: KotlinVisitorContext): Ordered {

    init {
        val javaClass = visitor.javaClass
        val resolver = visitorContext.resolver
        val declaration = resolver.getClassDeclarationByName(javaClass.name)

        if (declaration != null) {
            //resolver.getTypeArgument()
//            val generics = declaration.asStarProjectedType().arguments.filter { typeArgument ->
//                typeArgument.type!!.resolve().declaration.qualifiedName!!.asString() == TypeElementVisitor::class.java.name
//            }
//            val typeName = generics[0].variance.declaringClass()
//            if (typeName == io.micronaut.annotation.processing.visitor.LoadedVisitor.OBJECT_CLASS) {
//                classAnnotation = visitor.classType
//            } else {
//                classAnnotation = typeName
//            }
//            val elementName = generics[1].toString()
//            if (elementName == io.micronaut.annotation.processing.visitor.LoadedVisitor.OBJECT_CLASS) {
//                elementAnnotation = visitor.elementType
//            } else {
//                elementAnnotation = elementName
//            }
//        } else {
//            val classes = GenericTypeUtils.resolveInterfaceTypeArguments(
//                aClass,
//                TypeElementVisitor::class.java
//            )
//            if (classes != null && classes.size == 2) {
//                val classGeneric = classes[0]
//                if (classGeneric == Any::class.java) {
//                    classAnnotation = visitor.classType
//                } else {
//                    classAnnotation = classGeneric.name
//                }
//                val elementGeneric = classes[1]
//                if (elementGeneric == Any::class.java) {
//                    elementAnnotation = visitor.elementType
//                } else {
//                    elementAnnotation = elementGeneric.name
//                }
//            } else {
//                classAnnotation = Any::class.java.name
//                elementAnnotation = Any::class.java.name
//            }
        }
    }

    fun matches(classDeclaration: KSClassDeclaration) = true
}
