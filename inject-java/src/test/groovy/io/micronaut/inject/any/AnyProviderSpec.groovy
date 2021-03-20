package io.micronaut.inject.any

import io.micronaut.context.BeanContext
import io.micronaut.context.BeanProvider
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors

class AnyProviderSpec extends Specification {
    @Shared @AutoCleanup BeanContext beanContext = BeanContext.run()

    void 'test any injection'() {
        when:
        def owner = beanContext.getBean(PetOwner)
        def dogBeanProvider = owner.dogBeanProvider
        def terrierProvider = owner.terrierProvider
        def catBeanProvider = owner.catBeanProvider

        then:
        owner.dog instanceof Dog
        dogBeanProvider.get() instanceof Dog
        dogBeanProvider.stream().collect(Collectors.toList()).size() == 2
        dogBeanProvider.iterator().toList().size() == 2
        dogBeanProvider.isPresent()
        !dogBeanProvider.isUnique()
        dogBeanProvider.isResolvable()
        terrierProvider.isPresent()
        terrierProvider.isResolvable()
        terrierProvider.get() instanceof Terrier
        !catBeanProvider.isPresent()
        !catBeanProvider.isResolvable()
        !catBeanProvider.isUnique()
        catBeanProvider.toList() == []

        when:
        boolean called = false
        dogBeanProvider.ifPresent({ Dog dog ->
            assert dog in Dog
            called = true
        })

        then:
        called
    }
}
