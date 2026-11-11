package io.micronaut.kotlin.processing.visitor.rounds

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.rounds.FailingCompilationVisitor

/**
 * A visitor that fails from {@link TypeElementVisitor#finishCompilation(io.micronaut.inject.visitor.VisitorContext)}
 * fails the compilation.
 */
class FinishCompilationFailureSpec extends AbstractKotlinCompilerSpec {

    void "test failing from finishCompilation fails the compilation"() {
        when:
        buildContext("""
package failing

class $className
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains(message + "failing.$className")

        where:
        className                           | message
        FailingCompilationVisitor.FAIL      | FailingCompilationVisitor.FAILED_MESSAGE
        FailingCompilationVisitor.THROW     | FailingCompilationVisitor.THROWN_MESSAGE
    }
}
