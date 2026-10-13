package io.micronaut.inject.visitor.rounds

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class FinishRoundFailureSpec extends AbstractBeanDefinitionSpec {

    void setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, FailingRoundVisitor.name)
    }

    void cleanup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, "")
    }

    void "test a visitor failing to finish its round fails the compilation"() {
        when:
        buildContext('''
package failinground

class Origin {
}
''')

        then:
        def e = thrown(Exception)
        e.message.contains('Error finishing the round of type visitor')
        e.message.contains('round failed')
    }

    void "test a processing exception of a round fails the compilation"() {
        when:
        buildContext('''
package failinground

class Processing {
}
''')

        then:
        def e = thrown(Exception)
        e.message.contains('the round of Processing failed')
    }

    static class FailingRoundVisitor implements TypeElementVisitor<Object, Object> {

        private ClassElement visited

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name.startsWith('failinground.')) {
                visited = element
            }
        }

        @Override
        void finishRound(VisitorContext visitorContext) {
            ClassElement element = visited
            visited = null
            if (element?.simpleName == 'Processing') {
                throw new ProcessingException(element, 'the round of Processing failed')
            }
            if (element != null) {
                throw new IllegalStateException('round failed')
            }
        }
    }
}
