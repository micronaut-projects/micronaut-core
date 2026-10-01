package io.micronaut.inject.lifecycle.registrationclose

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.inject.BeanIdentifier
import spock.lang.Specification

class RegistrationCloseListenersSpec extends Specification {

    void "closing a registration of a bean without destroy logic triggers destruction listeners"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        SimpleBeanPreDestroyListener preListener = context.getBean(SimpleBeanPreDestroyListener)
        SimpleBeanDestroyedListener postListener = context.getBean(SimpleBeanDestroyedListener)
        SimpleBean bean = context.getBean(SimpleBean)

        when:
        BeanRegistration<SimpleBean> registration = BeanRegistration.of(
                context,
                BeanIdentifier.of(SimpleBean.name),
                context.getBeanDefinition(SimpleBean),
                bean
        )

        then:
        preListener.destroyed.empty
        postListener.destroyed.empty

        when:
        registration.close()

        then:
        preListener.destroyed == [bean]
        postListener.destroyed == [bean]

        cleanup:
        context.close()
    }

    void "closing a registration destroys dependent beans and triggers their listeners"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        OwnerBeanPreDestroyListener ownerPre = context.getBean(OwnerBeanPreDestroyListener)
        OwnerBeanDestroyedListener ownerPost = context.getBean(OwnerBeanDestroyedListener)
        DependentBeanPreDestroyListener dependentPre = context.getBean(DependentBeanPreDestroyListener)
        DependentBeanDestroyedListener dependentPost = context.getBean(DependentBeanDestroyedListener)

        when:
        BeanRegistration<OwnerBean> registration = context.getBeanRegistrations(OwnerBean).first()
        OwnerBean owner = registration.bean()

        then:
        ownerPre.destroyed.empty
        dependentPre.destroyed.empty

        when:
        registration.close()

        then:
        ownerPre.destroyed == [owner]
        ownerPost.destroyed == [owner]
        dependentPre.destroyed == [owner.dependent]
        dependentPost.destroyed == [owner.dependent]

        cleanup:
        context.close()
    }

    void "closing a synthetic container element registration does not destroy through the container definition"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        ElementFactory.CONTAINER_CREATIONS.set(0)
        ContainedElementPreDestroyListener preListener = context.getBean(ContainedElementPreDestroyListener)
        ContainedElementDestroyedListener postListener = context.getBean(ContainedElementDestroyedListener)

        when:
        List<BeanRegistration<ContainedElement>> registrations = context.getBeanRegistrations(ContainedElement).toList()

        then:
        registrations.size() == 2
        ElementFactory.CONTAINER_CREATIONS.get() == 1

        when: "the synthetic per element registrations are closed"
        registrations*.close()
        List<ContainedElement> elementsAfterClose = context.getBeanRegistrations(ContainedElement)*.bean()

        then: "no destruction runs with the mismatched container definition"
        preListener.destroyed.empty
        postListener.destroyed.empty

        and: "the container singleton is not purged"
        ElementFactory.CONTAINER_CREATIONS.get() == 1
        elementsAfterClose == registrations*.bean()

        cleanup:
        context.close()
    }

    void "closing a synthetic container element registration still stops a LifeCycle element"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        BeanRegistration<StoppableElement> registration = context.getBeanRegistrations(StoppableElement).first()

        then:
        registration.bean().running

        when:
        registration.close()

        then:
        !registration.bean().running

        cleanup:
        context.close()
    }

    void "a pre destroy listener can replace the bean instance"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        SimpleBeanPreDestroyListener preListener = context.getBean(SimpleBeanPreDestroyListener)
        SimpleBeanDestroyedListener postListener = context.getBean(SimpleBeanDestroyedListener)
        SimpleBean bean = context.getBean(SimpleBean)
        SimpleBean replacement = new SimpleBean()
        preListener.replacement = replacement

        when:
        BeanRegistration.of(
                context,
                BeanIdentifier.of(SimpleBean.name),
                context.getBeanDefinition(SimpleBean),
                bean
        ).close()

        then:
        preListener.destroyed == [bean]
        postListener.destroyed == [replacement]

        cleanup:
        context.close()
    }
}
