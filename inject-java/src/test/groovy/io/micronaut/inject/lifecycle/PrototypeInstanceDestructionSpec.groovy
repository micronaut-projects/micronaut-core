package io.micronaut.inject.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * Which beans a prototype takes with it when it is destroyed, and by which call.
 *
 * <p>The registration created alongside a bean is the only record of the beans that were created for it. Nothing
 * tracks the registration of a prototype - tracking it is what prototype scope exists not to do - so a caller that
 * wants a prototype destroyed with its dependents has to keep the registration rather than the bean, and
 * {@code destroyBean(Object)} on the instance alone can only run the bean's own pre-destroy. Both halves of that
 * are pinned here, because the second one reads like a defect until the first one is in view.</p>
 */
class PrototypeInstanceDestructionSpec extends AbstractTypeElementSpec {

    /**
     * A snapshot, because Spock renders a failed condition lazily and the list keeps being appended to by the
     * cleanup that closes the context.
     */
    private static List<String> recorded(Class<?> callsType) {
        new ArrayList<String>(callsType.RECORDED)
    }

    private static final String SOURCE = '''
package prototypedestruction;

import io.micronaut.context.annotation.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.*;

class Calls {
    static final List<String> RECORDED = new ArrayList<>();
}

@Prototype
class Dependent {
    private static int counter;

    final int id = ++counter;

    @PreDestroy
    public void close() {
        Calls.RECORDED.add("dependent-destroyed:" + id);
    }
}

@Prototype
class Root {
    final Dependent dependent;

    Root(Dependent dependent) {
        this.dependent = dependent;
    }

    @PreDestroy
    public void close() {
        Calls.RECORDED.add("root-destroyed");
    }
}
'''

    void 'test destroying the registration of a prototype destroys the dependents created with it'() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Class<?> callsType = context.classLoader.loadClass('prototypedestruction.Calls')

        when:
        def registration = context.getBeanRegistration(context.classLoader.loadClass('prototypedestruction.Root'), null)
        int dependentId = registration.bean.dependent.id
        context.destroyBean(registration)

        then:
        recorded(callsType) == ['root-destroyed', "dependent-destroyed:$dependentId".toString()]

        cleanup:
        context.close()
    }

    void 'test destroying a prototype instance alone cannot destroy the dependents created with it'() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Class<?> callsType = context.classLoader.loadClass('prototypedestruction.Calls')

        when:
        def root = context.getBean(context.classLoader.loadClass('prototypedestruction.Root'))
        int dependentId = root.dependent.id
        context.destroyBean(root)

        then: 'only the bean itself is destroyed; the context holds no registration to reach its dependents through'
        dependentId == 1
        recorded(callsType) == ['root-destroyed']

        cleanup:
        context.close()
    }
}
