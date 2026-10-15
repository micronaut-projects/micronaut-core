from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest

import java

@MicronautTest
class TypeHierarchySpec:
    @Test
    def test_reads_the_recorded_hierarchy(self):
        # tag::usage[]
        Payload = java.type("micronaut.docs.ioc.hierarchy.Payload")
        TypeHierarchies = java.type("io.micronaut.core.type.TypeHierarchies")
        Argument = java.type("io.micronaut.core.type.Argument")
        hierarchy = TypeHierarchies.find(Payload).orElseThrow() # <1>

        variables = hierarchy.getTypeVariables() # <2>
        superclass = hierarchy.getGenericSuperclass().orElseThrow() # <3>
        closure = hierarchy.getTypeClosure(Argument.of(Payload, java.type("java.lang.Integer"))) # <4>
        # end::usage[]
        assert variables.get(0).getName() == "T"
        assert variables.get(0).getType().getName() == "java.lang.Number"
        assert superclass.getType().getName() == "micronaut.docs.ioc.hierarchy.Base"
        assert closure.get(1).getTypeParameters()[0].getType().getName() == "java.lang.Integer"
        assert closure.get(closure.size() - 1).getType().getName() == "java.lang.Object"
        ArrayList = java.type("java.util.ArrayList")
        assert TypeHierarchies.find(ArrayList).orElseThrow().getTypeVariables().get(0).getName() == "E"
