from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest

import java


@MicronautTest(startApplication=False)
class CreatorSpec:
    @Test
    def test_creator_instantiate(self):
        Business = java.type("micronaut.docs.ioc.beans.Business")
        BeanIntrospection = java.type("io.micronaut.core.beans.BeanIntrospection")
        introspection = BeanIntrospection.getIntrospection(Business)
        b = introspection.instantiate("Acme")
        assert b.asPolyglotValue().name == "Acme"

    @Test
    def test_creator_constructors(self):
        Vendor = java.type("micronaut.docs.ioc.beans.Vendor")
        BeanIntrospection = java.type("io.micronaut.core.beans.BeanIntrospection")
        introspection = BeanIntrospection.getIntrospection(Vendor)
        constructors = introspection.getConstructors()
        assert constructors.size() == 2, "Expected the __init__ and the @Creator constructor"
        for constructor in constructors:
            assert constructor.instantiate("Acme").asPolyglotValue().name == "Acme"
