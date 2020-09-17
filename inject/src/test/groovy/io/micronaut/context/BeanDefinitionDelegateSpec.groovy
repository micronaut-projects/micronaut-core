package io.micronaut.context

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

import javax.inject.Singleton

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

    void "test order"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(["spec.name": getClass().simpleName])

        when:
        Collection<OrderedBean> beans = ctx.getBeansOfType(OrderedBean)

        then:
        beans[0].class == One
        beans[1].class == Ten
        beans[2].class == Fifty
        beans[3].class == Hundred
    }

    static interface OrderedBean {
    }

    @Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
    @Singleton
    @Order(value = Ordered.HIGHEST_PRECEDENCE)
    private static class One implements OrderedBean {
    }

    @Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
    @Singleton
    @Order(value = 10)
    private static class Ten implements OrderedBean {
    }

    @Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
    @Singleton
    private static class Fifty implements OrderedBean, Ordered {
        @Override
        int getOrder() {
            return 50
        }
    }

    @Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
    @Singleton
    @Order(value = 100)
    private static class Hundred implements OrderedBean {
    }

}
