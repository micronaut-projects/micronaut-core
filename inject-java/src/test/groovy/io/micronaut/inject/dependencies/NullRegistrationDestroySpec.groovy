package io.micronaut.inject.dependencies

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class NullRegistrationDestroySpec extends AbstractTypeElementSpec {
    private static final String SOURCE = '''
package nulldestroy;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
class Log { static final List<String> events = new CopyOnWriteArrayList<>(); }
@Prototype class Resource {
    @PreDestroy void stop() { Log.events.add("resource"); }
}
class Product { }
@Factory class Products {
    @Prototype @Nullable Product product(Resource resource) { return null; }
}
@Singleton class Listener implements BeanPreDestroyEventListener<Object>, BeanDestroyedEventListener<Object> {
    @Override public Object onPreDestroy(BeanPreDestroyEvent<Object> event) {
        Log.events.add("preDestroy " + event.getBeanDefinition().getBeanType().getSimpleName());
        return event.getBean();
    }
    @Override public void onDestroyed(BeanDestroyedEvent<Object> event) {
        Log.events.add("destroyed " + event.getBeanDefinition().getBeanType().getSimpleName());
    }
}
'''

    void "destroying a null bean registration skips the pre-destroy listeners and closes the dependents - #description"() {
        given:
        def context = buildContext(SOURCE)
        def log = context.classLoader.loadClass('nulldestroy.Log')
        def definition = context.getBeanDefinition(context.classLoader.loadClass('nulldestroy.Product'))
        def group = context.createDependencyGroup()
        def registration = group.createBeanRegistration(definition)

        expect:
        registration.bean() == null
        log.events.empty

        when:
        destroy(context, group, registration)

        then:
        log.events == ['preDestroy Resource', 'resource', 'destroyed Resource']

        when: "destroying again is a no-op"
        destroy(context, group, registration)

        then:
        log.events.size() == 3

        cleanup:
        context.close()

        where:
        description               | destroy
        'closing the group'       | { ctx, grp, reg -> grp.close() }
        'destroying in the group' | { ctx, grp, reg -> grp.destroy(reg) }
        'destroying in the context' | { ctx, grp, reg -> ctx.destroyBean(reg) }
    }
}
