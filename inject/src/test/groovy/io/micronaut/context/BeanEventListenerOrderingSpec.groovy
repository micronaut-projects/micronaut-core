package io.micronaut.context

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.core.type.Argument
import jakarta.inject.Singleton
import spock.lang.Specification

class BeanEventListenerOrderingSpec extends Specification {

    void "test order"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(["spec.name": getClass().simpleName])

        when:
        Collection<OrderedBean> beans = ctx.getBeansOfType(Argument.of(BeanCreatedEventListener, DummyBean))

        then:
        beans.size() == 6
        beans[0].class == NegativeOne
        beans[1].class == Zero
        beans[2].class == One
        beans[3].class == Ten
        beans[4].class == Fifty
        beans[5].class == Hundred

        when:
        ctx.getBean(DummyBeanSubclass)

        then:
        ON_CREATION_CALLBACKS.size() == 6
        ON_CREATION_CALLBACKS[0].class == NegativeOne
        ON_CREATION_CALLBACKS[1].class == Zero
        ON_CREATION_CALLBACKS[2].class == One
        ON_CREATION_CALLBACKS[3].class == Ten
        ON_CREATION_CALLBACKS[4].class == Fifty
        ON_CREATION_CALLBACKS[5].class == Hundred
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

    @Bean
    static class DummyBean {
    }

    @Bean
    static class DummyBeanSubclass extends DummyBean {
    }

    static List<OrderedBean> ON_CREATION_CALLBACKS = new ArrayList<>(10);

    static interface OrderedBean extends BeanCreatedEventListener<DummyBean> {
        @Override
        default DummyBean onCreated(@NonNull BeanCreatedEvent<DummyBean> event) {
            // Verify the ordering of event listener beans when the type is the same.
            ON_CREATION_CALLBACKS.add(this);
            return event.getBean();
        }
    }

    static interface ImplementsOrdered extends Ordered {
    }

    @Requires(property = "spec.name", value = "BeanEventListenerOrderingSpec")
    @Singleton
    @Order
    private static class Zero implements OrderedBean {
    }

    @Requires(property = "spec.name", value = "BeanEventListenerOrderingSpec")
    @Singleton
    @Order(-1)
    private static class NegativeOne implements BeanCreatedEventListener<DummyBeanSubclass> {
        @Override
        DummyBeanSubclass onCreated(@NonNull BeanCreatedEvent<DummyBeanSubclass> event) {
            // Verify the ordering of event listener beans when the type is the same.
            ON_CREATION_CALLBACKS.add(this);
            return event.getBean();
        }
    }

    @Requires(property = "spec.name", value = "BeanEventListenerOrderingSpec")
    @Singleton
    @Order(value = 1)
    private static class One implements OrderedBean {
    }

    @Requires(property = "spec.name", value = "BeanEventListenerOrderingSpec")
    @Singleton
    @Order(value = 10)
    private static class Ten implements OrderedBean {
    }

    @Requires(property = "spec.name", value = "BeanEventListenerOrderingSpec")
    @Singleton
    @Order(200) // The order annotation should be ignored because Ordered is implemented
    private static class Fifty implements OrderedBean, Ordered {
        int order = 50
    }

    @Requires(property = "spec.name", value = "BeanEventListenerOrderingSpec")
    @Singleton
    @Order(value = 100)
    private static class Hundred implements OrderedBean {
    }

    @Requires(property = "spec.name", value = "BeanEventListenerOrderingSpec")
    @Singleton
    private static class Negative100 implements ImplementsOrdered {
        int order = -100
    }

    @Requires(property = "spec.name", value = "BeanEventListenerOrderingSpec")
    @Singleton
    private static class Negative50 implements ImplementsOrdered {
        int order = -50
    }

    @Requires(property = "spec.name", value = "BeanEventListenerOrderingSpec")
    @Singleton
    @Order(-1000) // the order annotation is ignored
    private static class Negative10 implements ImplementsOrdered {
        int order = -10
    }


}
