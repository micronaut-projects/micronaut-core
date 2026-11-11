package io.micronaut.inject.generics

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class ProtectedNestedTypeArgumentsSpec extends AbstractTypeElementSpec {

    void "a #kind nested class of another package is kept in the type arguments"(String kind, String payload, String lib) {
        given:
        def context = buildContext(new JavaFiles()
            .add('lib.AbstractConsumer', lib)
            .add('app.MyConsumer', '''
package app;

@jakarta.inject.Singleton
public class MyConsumer extends lib.AbstractConsumer {
}
'''))
        def consumerType = context.classLoader.loadClass('app.MyConsumer')

        when:
        def consumer = context.getBean(consumerType)
        def definition = context.getBeanDefinition(consumerType)

        then:
        consumer != null
        definition.getTypeArguments(java.util.function.Consumer)*.type*.name == [payload]

        cleanup:
        context.close()

        where:
        kind                                   | payload                          | lib
        'protected static'                     | 'lib.AbstractConsumer$Payload'   | '''
package lib;

public abstract class AbstractConsumer implements java.util.function.Consumer<AbstractConsumer.Payload> {
    protected static class Payload { }
    public void accept(Payload payload) { }
}
'''
        'protected static of a public static'  | 'lib.AbstractConsumer$Inner$Payload' | '''
package lib;

public abstract class AbstractConsumer implements java.util.function.Consumer<AbstractConsumer.Inner.Payload> {
    public static class Inner {
        protected static class Payload { }
    }
    public void accept(Inner.Payload payload) { }
}
'''
    }
}
