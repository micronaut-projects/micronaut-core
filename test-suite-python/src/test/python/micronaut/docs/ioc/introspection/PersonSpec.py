import java
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest(startApplication=False)
class PersonSpec:

    @Test
    def testPersonIntrospection(self) -> None:
        # tag::usage[]
        BeanIntrospection = java.type("io.micronaut.core.beans.BeanIntrospection")
        Person = java.type("micronaut.docs.ioc.introspection.Person")
        introspection = BeanIntrospection.getIntrospection(Person)
        person = introspection.instantiate("John", 42)

        assert person.name == "John"
        assert person.age == 42
        # end::usage[]
