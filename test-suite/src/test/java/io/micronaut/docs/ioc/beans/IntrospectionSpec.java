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

    public void testNullable() {
        final BeanIntrospection<Manufacturer> introspection = BeanIntrospection.getIntrospection(Manufacturer.class);
        Manufacturer manufacturer = introspection.instantiate(null, "John");
        assertEquals("John", manufacturer.getName());

        final BeanProperty<Manufacturer, String> property = introspection.getRequiredProperty("name", String.class);
        property.set(manufacturer, "Fred");
        String name = property.get(manufacturer);

        assertEquals("Fred", name);
    }

    public void testVehicle() {
        final BeanIntrospection<Vehicle> introspection = BeanIntrospection.getIntrospection(Vehicle.class);
        Vehicle vehicle = introspection.instantiate("Subaru", "WRX", 2);
        assertEquals("Subaru", vehicle.getMake());
        assertEquals("WRX", vehicle.getModel());
        assertEquals(2, vehicle.getAxels());
    }

    public void testBusiness() {
        final BeanIntrospection<Business> introspection = BeanIntrospection.getIntrospection(Business.class);
        Business business = introspection.instantiate("Apple");
        assertEquals("Apple", business.getName());
    }
}
