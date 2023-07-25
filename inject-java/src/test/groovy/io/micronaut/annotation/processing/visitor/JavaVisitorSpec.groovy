package io.micronaut.annotation.processing.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class JavaVisitorSpec extends AbstractTypeElementSpec {
    private List<TypeElementVisitor> localTypeElementVisitors

    def 'visit enum methods'() {
        given:
        List<MethodElement> methods = []

        def enumVisitor = new TypeElementVisitor<Object, Object>() {
            @Override
            void visitMethod(MethodElement element, VisitorContext context) {
                methods.add(element)
            }
        }
        localTypeElementVisitors = [enumVisitor]

        buildClassLoader('example.Foo', '''
package example;
enum Foo {A, B;
    public String getValue() {
        return this == A ? "AA" : "BB";
    }
}
''')

        methods = methods.sort{it.name}

        expect:
        methods.size() == 3
        methods[0].name == "getValue"
        methods[0].returnType.name == "java.lang.String"
        methods[0].parameters.size() == 0

        methods[1].name == "valueOf"
        methods[2].name == "values"
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return localTypeElementVisitors
    }
}
