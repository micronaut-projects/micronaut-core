package io.micronaut.inject.visitor.rounds

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.visitor.TypeElementVisitor

/**
 * A visitor that fails from {@link TypeElementVisitor#finishCompilation(io.micronaut.inject.visitor.VisitorContext)}
 * fails the compilation.
 */
class FinishCompilationFailureSpec extends AbstractTypeElementSpec {

    void "test failing from finishCompilation fails the compilation"() {
        when:
        buildBeanDefinition("failing.$className", """
package failing;

class $className {
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains(message + "failing.$className")

        where:
        className                           | message
        FailingCompilationVisitor.FAIL      | FailingCompilationVisitor.FAILED_MESSAGE
        FailingCompilationVisitor.THROW     | FailingCompilationVisitor.THROWN_MESSAGE
    }

    void "test a bean registered again for the same element in a later round fails the compilation"() {
        when:"the bean is registered in two rounds with the same originating element, so with the same name"
        buildBeanDefinition('duplicate.Twice', '''
package duplicate;

class Twice {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('$Twice$RegisteredRecord0$Definition')
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new FailingCompilationVisitor(), new TwiceRegisteringVisitor()]
    }
}
