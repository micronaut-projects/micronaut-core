package io.micronaut.annotation.processing

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class WriteFailureSpec extends AbstractTypeElementSpec {

    void "a bean definition that cannot be written is a compile error on its bean"() {
        when:
        buildBeanDefinition('writefailure.Taken', '''
package writefailure;

@jakarta.inject.Singleton
class Taken {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Taken.java:5: error: Unexpected error: ')
        e.message.contains('writefailure.$Taken$Definition')
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new TakingVisitor()]
    }

    /**
     * Writes the class that the bean definition of {@code Taken} would be written to.
     */
    static class TakingVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'writefailure.Taken') {
                context.visitClass('writefailure.$Taken$Definition', element).close()
            }
        }
    }
}
