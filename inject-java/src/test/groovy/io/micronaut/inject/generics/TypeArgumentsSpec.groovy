package io.micronaut.inject.generics

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.inject.BeanDefinition

class TypeArgumentsSpec extends AbstractTypeElementSpec {

    void "test type arguments are passed through to the parent"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ChainA','''\
package test;

import jakarta.inject.Singleton;

@Singleton
class ChainA extends ChainB<Boolean> {
}

class ChainB<A> extends ChainC<A, Number, Integer> {
}

abstract class ChainC<A, B, E> implements ChainD<A, B, String, E> {
}

interface ChainD<A, B, C, E> extends ChainE<A, B, C, Byte> {
}

interface ChainE<A, B, C, D> {
}
''')

        expect:
        definition.getTypeArguments("test.ChainB").size() == 1
        definition.getTypeArguments("test.ChainB")[0].type == Boolean
        definition.getTypeArguments("test.ChainC").size() == 3
        definition.getTypeArguments("test.ChainC")[0].type == Boolean
        definition.getTypeArguments("test.ChainC")[1].type == Number
        definition.getTypeArguments("test.ChainC")[2].type == Integer
        definition.getTypeArguments("test.ChainD").size() == 4
        definition.getTypeArguments("test.ChainD")[0].type == Boolean
        definition.getTypeArguments("test.ChainD")[1].type == Number
        definition.getTypeArguments("test.ChainD")[2].type == String
        definition.getTypeArguments("test.ChainD")[3].type == Integer
        definition.getTypeArguments("test.ChainE").size() == 4
        definition.getTypeArguments("test.ChainE")[0].type == Boolean
        definition.getTypeArguments("test.ChainE")[1].type == Number
        definition.getTypeArguments("test.ChainE")[2].type == String
        definition.getTypeArguments("test.ChainE")[3].type == Byte
    }

    void "test argument is provider"() {
        def context = ApplicationContext.run(["spec.name": TypeArgumentsSpec.simpleName])
        def beanDefinition = context.getBeanDefinition(MyBean)

        expect:
        beanDefinition.requiredComponents.contains(String)
        beanDefinition.constructor.getArguments()[0].isProvider()

        cleanup:
        context.close()
    }

    void "a type argument the definition cannot name is left out of the type arguments"() {
        given: "a public supertype whose argument is package-private in another package"
        def context = buildContext(new JavaFiles()
            .add('hidden.PublicSupplier', '''
package hidden;

public abstract class PublicSupplier implements java.util.function.Supplier<HiddenSupplied> {
    public HiddenSupplied get() { return new HiddenSupplied(); }
}
''')
            .add('hidden.HiddenSupplied', '''
package hidden;

final class HiddenSupplied { }
''')
            .add('feeding.Feeder', '''
package feeding;

@jakarta.inject.Singleton
public class Feeder extends hidden.PublicSupplier {
}
'''))
        def feederType = context.classLoader.loadClass('feeding.Feeder')

        when: "the bean is resolved"
        def feeder = context.getBean(feederType)
        def definition = context.getBeanDefinition(feederType)

        then: "the definition loads and the supertype the definition cannot describe has no type arguments"
        feeder.get().getClass().name == 'hidden.HiddenSupplied'
        definition.getTypeArguments(java.util.function.Supplier).isEmpty()
        definition.getTypeArguments('hidden.PublicSupplier').isEmpty()

        cleanup:
        context.close()
    }

    void "a nested type argument the definition cannot name is left out of the type arguments"() {
        given: "a public supertype whose argument names a package-private nested class, next to a supertype it can name"
        def context = buildContext(new JavaFiles()
            .add('hidden.PublicSupplier', '''
package hidden;

public abstract class PublicSupplier implements java.util.function.Supplier<java.util.List<PublicSupplier.Hidden>>, java.util.function.IntSupplier {
    static final class Hidden { }
    public java.util.List<Hidden> get() { return java.util.List.of(); }
    public int getAsInt() { return 1; }
}
''')
            .add('feeding.Feeder', '''
package feeding;

@jakarta.inject.Singleton
public class Feeder extends hidden.PublicSupplier implements java.util.function.Function<String, Integer> {
    public Integer apply(String s) { return 1; }
}
'''))
        def feederType = context.classLoader.loadClass('feeding.Feeder')

        when: "the bean is resolved"
        def feeder = context.getBean(feederType)
        def definition = context.getBeanDefinition(feederType)

        then: "the definition loads, keeping the type arguments it can name"
        feeder.getAsInt() == 1
        definition.getTypeArguments(java.util.function.Supplier).isEmpty()
        definition.getTypeArguments(java.util.function.Function)*.type == [String, Integer]

        cleanup:
        context.close()
    }
}
