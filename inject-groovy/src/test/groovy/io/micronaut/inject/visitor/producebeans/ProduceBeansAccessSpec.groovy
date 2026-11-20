package io.micronaut.inject.visitor.producebeans

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.qualifiers.Qualifiers

class ProduceBeansAccessSpec extends AbstractBeanDefinitionSpec {

    void setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, ProduceBeansVisitor.name)
    }

    void cleanup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, "")
    }

    void "test produceBeans produces from non-public members and primitive and array products"() {
        given:
        def context = buildContext('''
package gproducebeans

import groovy.transform.PackageScope
import io.micronaut.core.annotation.ReflectiveAccess

class AccessProducers {

    @PackageScope
    final int produceField = 7

    Product producePublic() {
        new Product("public")
    }

    @PackageScope
    Product producePackagePrivate() {
        new Product("package-private")
    }

    protected Product produceProtected() {
        new Product("protected")
    }

    @ReflectiveAccess
    private Product produceReflective() {
        new Product("reflective")
    }

    private Product producePrivate() {
        new Product("private")
    }

    int produceNumber() {
        42
    }

    String[] produceNames() {
        ['left', 'right'] as String[]
    }

    void produceNothing() {
    }
}

class Product {
    final String name

    Product(String name) {
        this.name = name
    }
}
''')
        def product = context.classLoader.loadClass('gproducebeans.Product')

        expect:"a product of every member a factory could produce from"
        ['producePublic', 'produceReflective'].collect {
            context.getBean(product, Qualifiers.byName(it)).name
        } == ['public', 'reflective']
        context.getBean(int, Qualifiers.byName('produceNumber')) == 42
        context.getBean(String[], Qualifiers.byName('produceNames')) == ['left', 'right'] as String[]

        and:"the package-private and protected members produce too: their definitions call them directly, and the test compiler loads the definitions in another class loader than the classes, so only the definitions are checked"
        context.findBeanDefinition(product, Qualifiers.byName('producePackagePrivate')).isPresent()
        context.findBeanDefinition(product, Qualifiers.byName('produceProtected')).isPresent()
        context.findBeanDefinition(int, Qualifiers.byName('produceField')).isPresent()

        and:"none of a private member without reflective access, or of one returning no value"
        !context.findBeanDefinition(product, Qualifiers.byName('producePrivate')).isPresent()
        !context.allBeanDefinitions.any { it.getClass().name.contains('ProduceNothing') }

        cleanup:
        context?.close()
    }
}
