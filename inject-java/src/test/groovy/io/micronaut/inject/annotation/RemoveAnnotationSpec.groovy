package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.AdvisedBeanType
import io.micronaut.inject.annotation.repeatable.Topic
import io.micronaut.inject.annotation.repeatable.Topics
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class RemoveAnnotationSpec extends AbstractTypeElementSpec {
    Collection<TypeElementVisitor> visitors = []
    def cleanup() {
        visitors.clear()
    }

    void 'test replace simple annotation'() {
        given:
        visitors.add(new ReplacingTypeElementVisitor(ScopeOne.name, Prototype.name))
        def definition = buildBeanDefinition('removeann.Test', '''
package removeann;

import io.micronaut.inject.annotation.ScopeOne;
import io.micronaut.context.annotation.Bean;

@ScopeOne
@Bean
class Test {

}
''')
        expect:
        definition
        definition.hasStereotype(AnnotationUtil.SCOPE)
        definition.hasDeclaredAnnotation(Prototype)
        !definition.hasDeclaredAnnotation(ScopeOne)
        def stereotypes = definition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE)
        stereotypes.contains(Prototype.name)
        stereotypes.size() == 1
    }

    void 'test remove simple annotation'() {
        given:
        visitors.add(new RemovingTypeElementVisitor(ScopeOne.name))
        def definition = buildBeanDefinition('removeann.Test', '''
package removeann;

import io.micronaut.inject.annotation.ScopeOne;
import io.micronaut.context.annotation.Bean;

@ScopeOne
@Bean
class Test {

}
''')
        expect:
        definition
        !definition.hasStereotype(AnnotationUtil.SCOPE)
        !definition.hasDeclaredAnnotation(Prototype)
        !definition.hasDeclaredAnnotation(ScopeOne)
        def stereotypes = definition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE)
        stereotypes.size() == 0
    }

    void "test remove stereotype"() {
        given:
        visitors.add(new RemoveStereotypeVisitor(ScopeOne.name))
        def definition = buildBeanDefinition('removeann.Test', '''
package removeann;

import io.micronaut.inject.annotation.ScopeTwo;
import io.micronaut.context.annotation.Bean;

@ScopeTwo
@Bean
class Test {

}
''')
        expect:"The ScopeOne stereotype was removed but Scope remains as it was not removed"
        definition
        !definition.hasDeclaredAnnotation(Prototype)
        !definition.hasStereotype(ScopeOne)
        !definition.hasDeclaredStereotype(ScopeOne)
        def stereotypes = definition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE)
        stereotypes.size() == 1
        stereotypes.contains(ScopeTwo.name)
    }

    void 'test remove if simple annotation'() {
        given:
        visitors.add(new RemovingIfTypeElementVisitor(ScopeOne.name))
        def definition = buildBeanDefinition('removeann.Test', '''
package removeann;

import io.micronaut.inject.annotation.ScopeOne;
import io.micronaut.context.annotation.Bean;

@ScopeOne
@Bean
class Test {

}
''')
        expect:
        definition
        !definition.hasStereotype(AnnotationUtil.SCOPE)
        !definition.hasDeclaredAnnotation(Prototype)
        !definition.hasDeclaredAnnotation(ScopeOne)
        def stereotypes = definition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE)
        stereotypes.size() == 0
    }

    void "test remove interceptor binding"() {
        given:
        visitors.add(new RemovingTypeElementVisitor(Trace.name))
        def definition = buildBeanDefinition('removeann.Test', '''
package removeann;

import io.micronaut.inject.annotation.Trace;
import io.micronaut.context.annotation.Bean;

@Trace(type = String.class, types = String.class)
@Bean
class Test {

}
''')
        expect:
        definition
        !(definition instanceof AdvisedBeanType)
        !definition.hasStereotype(AnnotationUtil.ANN_AROUND)
        !definition.hasAnnotation(AnnotationUtil.ANN_AROUND)
        !definition.hasAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)
    }

    void "test remove interceptor binding results in it no longer being a bean if no bean defining annotation present"() {
        given:
        visitors.add(new RemovingTypeElementVisitor(Trace.name))
        def definition = buildBeanDefinition('removeann.Test', '''
package removeann;

import io.micronaut.inject.annotation.Trace;
import io.micronaut.context.annotation.Bean;

@Trace(type = String.class, types = String.class)
class Test {

}
''')
        expect:
        definition == null
    }

    void "test removing a repeatable type removes all"() {
        given:
        visitors.add(new RemovingTypeElementVisitor(Topic.name))
        def definition = buildBeanDefinition('removeann.Test', '''
package removeann;

import io.micronaut.inject.annotation.ScopeOne;
import io.micronaut.context.annotation.Bean;
import io.micronaut.inject.annotation.repeatable.Topic;

@Topic("one")
@Topic("two")
@Bean
class Test {

}
''')
        expect:
        definition
        !definition.hasDeclaredAnnotation(Topics)
        !definition.hasDeclaredAnnotation(Topic)
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return visitors
    }

    static class RemovingTypeElementVisitor implements TypeElementVisitor<Object, Object> {
        private final String annotationName;

        RemovingTypeElementVisitor(String annotationName) {
            this.annotationName = annotationName
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            element.removeAnnotation(annotationName)
        }
    }

    static class RemovingIfTypeElementVisitor implements TypeElementVisitor<Object, Object> {
        private final String annotationName;

        RemovingIfTypeElementVisitor(String annotationName) {
            this.annotationName = annotationName
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            element.removeAnnotationIf({  it.annotationName == annotationName })
        }
    }

    static class ReplacingTypeElementVisitor implements TypeElementVisitor<Object, Object> {
        private final String annotationName;
        private final String replacement;

        ReplacingTypeElementVisitor(String annotationName, String replacement) {
            this.annotationName = annotationName
            this.replacement = replacement
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            element.removeAnnotation(annotationName)
            element.annotate(replacement)
        }
    }

    static class RemoveStereotypeVisitor implements TypeElementVisitor<Object, Object> {
        private final String annotationName;

        RemoveStereotypeVisitor(String annotationName) {
            this.annotationName = annotationName
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            element.removeStereotype(annotationName)
        }
    }
}
