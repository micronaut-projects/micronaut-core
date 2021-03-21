package io.micronaut.inject.any

import io.micronaut.context.BeanContext
import io.micronaut.context.BeanProvider
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
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
        def terrierProvider2 = beanContext.getBean(
                BeanProvider.argumentOf(Dog),
                Qualifiers.byName("terrier")
        )

        then:
        owner.dog instanceof Dog
        dogBeanProvider.get() instanceof Dog
        dogBeanProvider.stream().collect(Collectors.toList()).size() == 2
        dogBeanProvider.iterator().toList().size() == 2
        dogBeanProvider.iterator().toList().contains(dogBeanProvider.get())
        dogBeanProvider.isPresent()
        !dogBeanProvider.isUnique()
        dogBeanProvider.isResolvable()
        terrierProvider.isPresent()
        terrierProvider.isUnique()
        terrierProvider.isResolvable()
        terrierProvider.get() instanceof Terrier
        terrierProvider.get().is(terrierProvider2.get())
        terrierProvider.toList().first().is(terrierProvider.get())
        terrierProvider.stream().findFirst().get().is(terrierProvider.get())
        !catBeanProvider.isPresent()
        !catBeanProvider.isResolvable()
        !catBeanProvider.isUnique()
        catBeanProvider.toList() == []
        catBeanProvider.stream().toArray() == [] as Object[]
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
