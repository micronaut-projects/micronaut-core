package io.micronaut.docs.ioc.introspection.pck;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.docs.ioc.introspection.pck.foobar.AbcPerson;
import io.micronaut.docs.ioc.introspection.pck.foobar.FoobarPerson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PackageIntrospectionTest {

    @Test
    void testPackageIntrospection() {
        BeanIntrospection<FoobarPerson> introspection1 = BeanIntrospection.getIntrospection(FoobarPerson.class);
        FoobarPerson person1 = introspection1.instantiate("John", 42);

        Assertions.assertEquals("John", person1.name());
        Assertions.assertEquals(42, person1.age());

        BeanIntrospection<AbcPerson> introspection2 = BeanIntrospection.getIntrospection(AbcPerson.class);
        AbcPerson person2 = introspection2.instantiate("John", 42);

        Assertions.assertEquals("John", person2.name());
        Assertions.assertEquals(42, person2.age());
    }
}
