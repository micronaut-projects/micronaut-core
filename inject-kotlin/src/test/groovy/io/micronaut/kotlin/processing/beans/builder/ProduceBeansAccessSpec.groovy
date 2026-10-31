package io.micronaut.kotlin.processing.beans.builder

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.qualifiers.Qualifiers

class ProduceBeansAccessSpec extends AbstractKotlinCompilerSpec {

    void "test produceBeans produces from non-public members and primitive and array products"() {
        given:
        def context = buildContext('''
package kproducebeans

import io.micronaut.core.annotation.ReflectiveAccess

class AccessProducers {

    @JvmField
    val produceField: Int = 7

    fun producePublic() = Product("public")

    protected fun produceProtected() = Product("protected")

    @ReflectiveAccess
    private fun produceReflective() = Product("reflective")

    private fun producePrivate() = Product("private")

    fun produceNumber(): Int = 42

    fun produceNames(): Array<String> = arrayOf("left", "right")

    fun produceNothing() {
    }
}

class Product(val name: String)
''')
        def product = context.classLoader.loadClass('kproducebeans.Product')

        expect:"a product of every member a factory could produce from"
        ['producePublic', 'produceProtected', 'produceReflective'].collect {
            context.getBean(product, Qualifiers.byName(it)).name
        } == ['public', 'protected', 'reflective']
        context.getBean(int, Qualifiers.byName('produceNumber')) == 42
        context.getBean(String[], Qualifiers.byName('produceNames')) == ['left', 'right'] as String[]
        context.getBean(int, Qualifiers.byName('produceField')) == 7

        and:"none of a private member without reflective access, or of one returning no value"
        !context.findBeanDefinition(product, Qualifiers.byName('producePrivate')).isPresent()
        !context.allBeanDefinitions.any { it.getClass().name.contains('ProduceNothing') }

        cleanup:
        context?.close()
    }
}
