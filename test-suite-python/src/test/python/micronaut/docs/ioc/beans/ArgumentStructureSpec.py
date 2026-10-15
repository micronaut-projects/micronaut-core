from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest

import java

@MicronautTest
class ArgumentStructureSpec:
    @Test
    def test_argument_structure(self):
        # tag::structure[]
        Catalog = java.type("micronaut.docs.ioc.beans.Catalog")
        BeanIntrospection = java.type("io.micronaut.core.beans.BeanIntrospection")
        Argument = java.type("io.micronaut.core.type.Argument")
        List = java.type("java.util.List")
        Object = java.type("java.lang.Object")

        introspection = BeanIntrospection.getIntrospection(Catalog)
        prices = introspection.getRequiredProperty("prices", List).asArgument() # list[float]
        items = introspection.getRequiredProperty("items", List).asArgument() # list[T]

        price = prices.getTypeParameters()[0]
        assert not price.isWildcard() # <1>
        assert not price.isUnresolvedTypeVariable()

        variable = items.getTypeParameters()[0]
        assert variable.isUnresolvedTypeVariable() # <2>
        assert variable.getVariableName() == "T"

        samples = variable.arrayType() # <3>
        assert samples.componentType().equalsStructure(variable)

        list_of_object = Argument.listOf(Object)
        assert items.equalsType(list_of_object) # <4>
        assert not items.equalsStructure(list_of_object)
        # end::structure[]
