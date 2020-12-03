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
        beans.size() == 4
        beans[0].class == One
        beans[1].class == Ten
        beans[2].class == Fifty
        beans[3].class == Hundred
    }

    void "test the @Order annotation is ignored if the bean type implements Ordered"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(["spec.name": getClass().simpleName])

        when:
        Collection<ImplementsOrdered> beans = ctx.getBeansOfType(ImplementsOrdered)

        then:
        beans.size() == 3
        beans[0].class == Negative100
        beans[1].class == Negative50
        beans[2].class == Negative10
    }

    static interface OrderedBean {
    }

    static interface ImplementsOrdered extends Ordered {

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
    @Order(200) // The order annotation should be ignored because Ordered is implemented
    private static class Fifty implements OrderedBean, Ordered {
        int order = 50
    }

    @Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
    @Singleton
    @Order(value = 100)
    private static class Hundred implements OrderedBean {
    }

    @Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
    @Singleton
    private static class Negative100 implements ImplementsOrdered {
        int order = -100
    }

    @Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
    @Singleton
    private static class Negative50 implements ImplementsOrdered {
        int order = -50
    }

    @Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
    @Singleton
    @Order(-1000) // the order annotation is ignored
    private static class Negative10 implements ImplementsOrdered {
        int order = -10
    }


}
