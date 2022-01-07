package io.micronaut.kotlin.processing

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
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
