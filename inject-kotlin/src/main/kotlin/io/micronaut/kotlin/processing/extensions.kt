package io.micronaut.kotlin.processing

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.symbol.*
import java.lang.StringBuilder

fun KSClassDeclaration.toClassName(): String {
    val className = StringBuilder(packageName.asString())
    val hierarchy = mutableListOf(this)
    var parentDeclaration = parentDeclaration
    while (parentDeclaration is KSClassDeclaration) {
        hierarchy.add(0, parentDeclaration)
        parentDeclaration = parentDeclaration.parentDeclaration
    }
    hierarchy.joinTo(className, "$", ".")
    return className.toString()
}

fun KSPropertyDeclaration.isTypeReference(): Boolean {
    var property: KSPropertyDeclaration? = this
    while (property != null) {
        if (property.type.resolve().declaration is KSTypeParameter) {
            return true
        }
        property = property.findOverridee()
    }
    return false
}

fun KSPropertySetter.getVisibility(): Visibility {
    return when {
        this.modifiers.contains(Modifier.PUBLIC) -> Visibility.PUBLIC
        this.modifiers.contains(Modifier.PRIVATE) -> Visibility.PRIVATE
        this.modifiers.contains(Modifier.PROTECTED) ||
                this.modifiers.contains(Modifier.OVERRIDE) -> Visibility.PROTECTED
        this.modifiers.contains(Modifier.INTERNAL) -> Visibility.INTERNAL
        else -> if (this.origin != Origin.JAVA && this.origin != Origin.JAVA_LIB)
            Visibility.PUBLIC else Visibility.JAVA_PACKAGE
    }
}
