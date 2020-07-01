package io.micronaut.context

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

class BeanDefinitionDelegateSpec extends Specification {

    void "test type arguments are retrieved"() {
        BeanDefinition beanDefinition = new AbstractBeanDefinition(String.class, AnnotationMetadata.EMPTY_METADATA, false) {
            @Override
            protected Map<String, Argument<?>[]> getTypeArgumentsMap() {
                [foo: [Argument.of(String)] as Argument<?>[]]
            }
        }

        when:
        BeanDefinition delegate = BeanDefinitionDelegate.create(beanDefinition)

        then:
        delegate.getTypeArguments('foo').size() == 1
        delegate.getTypeArguments('foo')[0].getType() == String.class
    }
}
