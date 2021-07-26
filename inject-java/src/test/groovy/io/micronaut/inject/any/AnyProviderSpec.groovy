package io.micronaut.inject.any

import io.micronaut.context.BeanContext
import io.micronaut.context.BeanProvider
import io.micronaut.context.exceptions.NonUniqueBeanException
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
        def anotherDogBeanProvider = beanContext.getBean(
                BeanProvider.argumentOf(Dog),
                Qualifiers.any()
        )

        then:
        beanContext.getBean(Object, Qualifiers.byName("poodle")) instanceof Poodle
        beanContext.findBean(Object, Qualifiers.byName("poodle")).isPresent()
        !beanContext.findBean(Object, Qualifiers.byName("doesn't exist")).isPresent()
        beanContext.containsBean(Object, Qualifiers.byName("poodle"))
        !beanContext.containsBean(Object, Qualifiers.byName("doesnt-exist"))
        owner.dog instanceof Dog
        dogBeanProvider.get(Qualifiers.byName("poodle")) instanceof Dog
        dogBeanProvider.stream().collect(Collectors.toList()).size() == 2
        dogBeanProvider.iterator().toList().size() == 2
        dogBeanProvider.iterator().toList().contains(dogBeanProvider.get(Qualifiers.byName("poodle")))
        dogBeanProvider.isPresent()
        !dogBeanProvider.isUnique()
        !dogBeanProvider.isResolvable()
        !anotherDogBeanProvider.isUnique()
        !anotherDogBeanProvider.isResolvable()
        anotherDogBeanProvider.isPresent()
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
        terrierProvider2.ifPresent({ Dog dog ->
            assert dog in Dog
            assert dog instanceof Terrier
            called = true
        })

        then:
        called

        when:
        called = false
        dogBeanProvider.ifPresent({ Dog dog ->
            called = true
        })

        then:
        thrown(NonUniqueBeanException)
        !called

        when:
        called = false
        dogBeanProvider.ifResolvable({ Dog dog ->
            called = true
        })

        then:
        !called
    }
}
