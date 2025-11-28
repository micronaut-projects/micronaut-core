from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from jakarta.inject import Inject
from typing import Annotated

import java

@MicronautTest
class IntrospectionSpec:
    @Test
    def test_introspected(self):
        # tag::usage[]
        Person = java.type("micronaut.docs.ioc.beans.Person")
        BeanIntrospection = java.type("io.micronaut.core.beans.BeanIntrospection")
        introspection = BeanIntrospection.getIntrospection(Person) # <1>
        person = introspection.instantiate("John", 40) # <2>
        print(f"Hello {person.asPolyglotValue().name}")

        property = introspection.getRequiredProperty("name", java.type("java.lang.String")) # <3>
        property.set(person, "Fred") # <4>
        print(f"Hello {person.asPolyglotValue().name}") # <5>
        # end::usage[]
        assert person.asPolyglotValue().name == "Fred", "Should have changed the attribute"
