package io.micronaut.inject.value

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.Value

class ValueParseSpec extends AbstractBeanDefinitionSpec{

    void 'test value annotation present'() {
        given:
        def definition = buildBeanDefinition('io.micronaut.inject.value.A', '''
package io.micronaut.inject.value;
import io.micronaut.context.annotation.Value;

@jakarta.inject.Singleton
class A {
    @Value('${foo.bar}')
    int port
}
''')
        expect:
        definition.injectedMethods.size() == 1
        definition.injectedMethods[0].arguments[0].annotationMetadata.stringValue(
                Value
        ).get() == '${foo.bar}'
    }
}
