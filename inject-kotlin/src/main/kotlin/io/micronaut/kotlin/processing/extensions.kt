package io.micronaut.kotlin.processing

import com.google.devtools.ksp.symbol.KSClassDeclaration
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
