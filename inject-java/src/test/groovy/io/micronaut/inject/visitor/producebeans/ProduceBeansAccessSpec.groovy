package io.micronaut.inject.visitor.producebeans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.TypeElementVisitor

class ProduceBeansAccessSpec extends AbstractTypeElementSpec {

    void "test produceBeans produces from non-public members and primitive and array products"() {
        given:
        def context = buildContext('producebeans.AccessProducers', '''
package producebeans;

import io.micronaut.core.annotation.ReflectiveAccess;

public class AccessProducers {

    final int produceField = 7;

    public Product producePublic() {
        return new Product("public");
    }

    Product producePackagePrivate() {
        return new Product("package-private");
    }

    protected Product produceProtected() {
        return new Product("protected");
    }

    @ReflectiveAccess
    private Product produceReflective() {
        return new Product("reflective");
    }

    private Product producePrivate() {
        return new Product("private");
    }

    public int produceNumber() {
        return 42;
    }

    public String[] produceNames() {
        return new String[] {"left", "right"};
    }

    public void produceNothing() {
    }
}

class Product {
    final String name;

    Product(String name) {
        this.name = name;
    }
}
''')
        def product = context.classLoader.loadClass('producebeans.Product')

        expect:"a product of every member a factory could produce from"
        ['producePublic', 'producePackagePrivate', 'produceProtected', 'produceReflective'].collect {
            context.getBean(product, Qualifiers.byName(it)).name
        } == ['public', 'package-private', 'protected', 'reflective']
        context.getBean(int, Qualifiers.byName('produceNumber')) == 42
        context.getBean(String[], Qualifiers.byName('produceNames')) == ['left', 'right'] as String[]
        context.getBean(int, Qualifiers.byName('produceField')) == 7

        and:"none of a private member without reflective access, or of one returning no value"
        !context.findBeanDefinition(product, Qualifiers.byName('producePrivate')).isPresent()
        !context.allBeanDefinitions.any { it.getClass().name.contains('ProduceNothing') }

        cleanup:
        context?.close()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new ProduceBeansVisitor()]
    }
}
