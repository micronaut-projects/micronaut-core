package io.micronaut.kotlin.processing.beans

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext

class BeanDefinitionVisitor(private val classDeclaration: KSClassDeclaration,
                            private val visitorContext: KotlinVisitorContext): KSDefaultVisitor<Any, Any>() {

    val beanDefinitionWriters: MutableList<BeanDefinitionWriter> = mutableListOf()

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Any): Any {
        return data
    }

    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Any): Any {
        return data
    }

    override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Any): Any {
        return data
    }

    override fun defaultHandler(node: KSNode, data: Any): Any {
        return data
    }
}
