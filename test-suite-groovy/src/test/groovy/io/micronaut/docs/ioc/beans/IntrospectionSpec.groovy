package io.micronaut.docs.ioc.beans

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanProperty
import io.micronaut.core.beans.BeanWrapper
import spock.lang.Specification

class IntrospectionSpec extends Specification {

    void "test retrieve introspection"() {
        given:
        // tag::usage[]
        def introspection = BeanIntrospection.getIntrospection(Person) // <1>
        Person person = introspection.instantiate("John") // <2>
        println("Hello $person.name")

        BeanProperty<Person, String> property = introspection.getRequiredProperty("name", String) // <3>
        property.set(person, "Fred") // <4>
        String name = property.get(person) // <5>
        println("Hello $person.name")
        // end::usage[]

        expect:
        name == 'Fred'
    }

    void testBeanWrapper() {
        given:
        // tag::wrapper[]
        final BeanWrapper<Person> wrapper = BeanWrapper.getWrapper(new Person("Fred")) // <1>

        wrapper.setProperty("age", "20") // <2>
        int newAge = wrapper.getRequiredProperty("age", Integer) // <3>

        println("Person's age now $newAge")
        // end::wrapper[]
        expect:
        newAge == 20
    }

    void testVehicle() {
        final BeanIntrospection<Vehicle> introspection = BeanIntrospection.getIntrospection(Vehicle)
        Vehicle vehicle = introspection.instantiate("Subaru", "WRX", 2)

        expect:
        "Subaru" == vehicle.make
        "WRX" == vehicle.model
        2 == vehicle.axles
    }

    void testBusiness() {
        final BeanIntrospection<Business> introspection = BeanIntrospection.getIntrospection(Business)
        Business business = introspection.instantiate("Apple")

        expect:
        "Apple" == business.name
    }

    void testUser() {
        when:
        final BeanIntrospection<User> introspection = BeanIntrospection.getIntrospection(User.class)
        User user = introspection.instantiate("Apple")

        then:
        user.name == "Apple"
        user.age == 18
        introspection.getBeanProperties().size() == 2

        when:
        introspection.getRequiredProperty("age", int).set(user, 23)

        then:
        user.age == 23
    }
}
