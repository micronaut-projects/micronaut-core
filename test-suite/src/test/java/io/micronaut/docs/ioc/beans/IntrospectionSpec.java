package io.micronaut.docs.ioc.beans;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import junit.framework.TestCase;

public class IntrospectionSpec extends TestCase {

    public void testRetrieveInspection() {

        // tag::usage[]
        final BeanIntrospection<Person> introspection = BeanIntrospection.getIntrospection(Person.class); // <1>
        Person person = introspection.instantiate("John"); // <2>
        System.out.println("Hello " + person.getName());

        final BeanProperty<Person, String> property = introspection.getRequiredProperty("name", String.class); // <3>
        property.set(person, "Fred"); // <4>
        String name = property.get(person); // <5>
        System.out.println("Hello " + person.getName());
        // end::usage[]

        assertEquals("Fred", name);
    }

    public void testBeanWrapper() {
        // tag::wrapper[]
        final BeanWrapper<Person> wrapper = BeanWrapper.getWrapper(new Person("Fred")); // <1>

        wrapper.setProperty("age", "20"); // <2>
        int newAge = wrapper.getRequiredProperty("age", int.class); // <3>

        System.out.println("Person's age now " + newAge);
        // end::wrapper[]
        assertEquals(20, newAge);
    }
}
