package io.micronaut.inject.visitor.rounds

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.visitor.TypeElementVisitor

/**
 * A visitor that fails from {@link TypeElementVisitor#finishCompilation(io.micronaut.inject.visitor.VisitorContext)}
 * fails the compilation.
 */
class FinishCompilationFailureSpec extends AbstractBeanDefinitionSpec {

    void setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, FailingCompilationVisitor.name)
    }

    void cleanup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, "")
    }

    void "test failing from finishCompilation fails the compilation"() {
        when:
        buildContext("""
package failing

class $className {
}
""")

        then:
        def e = thrown(Exception)
        e.message.contains(message + "failing.$className")

        where:
        className                           | message
        FailingCompilationVisitor.FAIL      | FailingCompilationVisitor.FAILED_MESSAGE
        FailingCompilationVisitor.THROW     | FailingCompilationVisitor.THROWN_MESSAGE
    }
}
