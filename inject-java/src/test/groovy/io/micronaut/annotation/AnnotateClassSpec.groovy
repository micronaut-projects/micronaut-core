package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Prototype
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class AnnotateClassSpec extends AbstractTypeElementSpec {

    void 'test annotating 1'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateClass', '''
package addann;

import io.micronaut.context.annotation.Executable;

class AnnotateClass {

    @Executable
    public String myMethod1() {
        return null;
    }

}

''')
        then:
            definition.hasAnnotation(MyAnnotation)
    }

    void 'test annotating 2'() {
        when:
            def definition = buildBeanDefinition('addann.Foobar1$AnnotateClass', '''
package addann;

import io.micronaut.context.annotation.Executable;

class Foobar1 {

    @Executable
    public String myMethod1() {
        return null;
    }

    static class AnnotateClass {

        @Executable
        public String myMethod2() {
            return null;
        }

    }

}

''')
        then:
            definition.hasAnnotation(MyAnnotation)
    }

    void 'test annotating 3'() {
        when:
            def definition = buildBeanDefinition('addann.Foobar2$AnnotateClass', '''
package addann;

import io.micronaut.context.annotation.Executable;

class Foobar2 {

    static class AnnotateClass {

        @Executable
        public String myMethod2() {
            return null;
        }

    }

}

''')
        then:
            definition.hasAnnotation(MyAnnotation)
    }

    void 'test annotating 4'() {
        when:
            def definition = buildBeanDefinition('addann.Foobar3$AnnotateClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Nullable;import io.micronaut.core.annotation.ReflectiveAccess;

class Foobar3 {

    static class AnnotateClass {

        @Executable
        @ReflectiveAccess
        private String myMethod2() {
            return null;
        }

    }

}

''')
        then:
            definition.hasAnnotation(MyAnnotation)
    }

    void 'test annotating 5'() {
        when:
            def definition = buildBeanDefinition('addann.Foobar4$AnnotateClass', '''
package addann;

import io.micronaut.context.annotation.Property;

class Foobar4 {

    static class AnnotateClass {

        @Property(name = "xyz") // Make the BeanDefinitionInjectProcessor to see the class
        private String myField;

    }

}

''')
        then:
            definition.hasAnnotation(MyAnnotation)
    }

    static class AnnotateClassVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName().endsWith("AnnotateClass")) {
                element.annotate(MyAnnotation)
                element.annotate(Prototype)

                // Validate the cache is working

                def newClassElement = context.getClassElement(element.name).get()
                assert newClassElement.hasAnnotation(MyAnnotation)
                assert newClassElement.hasAnnotation(Prototype)
            }
        }
    }

}
