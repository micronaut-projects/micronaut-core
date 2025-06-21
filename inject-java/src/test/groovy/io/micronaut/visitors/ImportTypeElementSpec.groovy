package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ClassImport
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.EnumConstantElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class ImportTypeElementSpec extends AbstractTypeElementSpec {

    void "test bean introspection annotate"() {
        given:
            ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.context.annotation.ClassImport;
import io.micronaut.core.annotation.Introspected;

@ClassImport(classes = io.micronaut.visitors.MySimpleClass.class, annotate = Introspected.class)
class Test {}
''')

        when:"the reference is loaded"
            def clazz = context.classLoader.loadClass('test.$io_micronaut_visitors_MySimpleClass$Introspection')
            BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is generated"
            reference != null

        cleanup:
            context?.close()
    }

    void "test bean introspection annotate name"() {
        given:
            ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.context.annotation.ClassImport;
import io.micronaut.core.annotation.Introspected;

@ClassImport(classes = io.micronaut.visitors.MySimpleClass.class, annotateNames = "io.micronaut.core.annotation.Introspected")
class Test {}
''')

        when:"the reference is loaded"
            def clazz = context.classLoader.loadClass('test.$io_micronaut_visitors_MySimpleClass$Introspection')
            BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is generated"
            reference != null

        cleanup:
            context?.close()
    }

    void 'test default'() {
        when:
            DefaultTypeElementVisitor.ENABLED = true
            def definition = buildBeanDefinition('test.Importer', '''
package test;

import io.micronaut.context.annotation.ClassImport;import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Import;import io.micronaut.context.annotation.Prototype;

@Prototype
@ClassImport(classes = io.micronaut.visitors.MySimpleClass.class)
class Importer {
}

''')
        then:
            definition
            !definition.hasAnnotation(ClassImport) // Source annotation is skipped
            DefaultTypeElementVisitor.VISITED_CLASSES.size() == 1
            DefaultTypeElementVisitor.VISITED_CONSTRUCTORS.size() == 1
            DefaultTypeElementVisitor.VISITED_FIELDS.size() == 2
            DefaultTypeElementVisitor.VISITED_ENUM_CONSTANTS.size() == 0
            DefaultTypeElementVisitor.VISITED_METHODS.size() == 4

        cleanup:
            DefaultTypeElementVisitor.cleanup()
    }

    static class DefaultTypeElementVisitor implements TypeElementVisitor<Object, Object> {

        static boolean ENABLED = false
        static List<ClassElement> VISITED_CLASSES = new ArrayList<>()
        static List<MethodElement> VISITED_METHODS = new ArrayList<>()
        static List<ConstructorElement> VISITED_CONSTRUCTORS = new ArrayList<>()
        static List<FieldElement> VISITED_FIELDS = new ArrayList<>()
        static List<EnumConstantElement> VISITED_ENUM_CONSTANTS = new ArrayList<>()

        static void cleanup() {
            VISITED_CLASSES.clear()
            VISITED_METHODS.clear()
            VISITED_CONSTRUCTORS.clear()
            VISITED_FIELDS.clear()
            VISITED_ENUM_CONSTANTS.clear()
            ENABLED = false
        }

        boolean shouldVisit;

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            shouldVisit = !element.hasAnnotation(ClassImport)
            if (shouldVisit()) {
                VISITED_CLASSES.add(element)
            }
        }

        private boolean shouldVisit() {
            ENABLED && shouldVisit
        }

        @Override
        void visitConstructor(ConstructorElement element, VisitorContext context) {
            if (shouldVisit()) {
                VISITED_CONSTRUCTORS.add(element)
            }
        }

        @Override
        void visitEnumConstant(EnumConstantElement element, VisitorContext context) {
            if (shouldVisit()) {
                VISITED_ENUM_CONSTANTS.add(element)
            }
        }

        @Override
        void visitField(FieldElement element, VisitorContext context) {
            if (shouldVisit()) {
                VISITED_FIELDS.add(element)
            }
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (shouldVisit()) {
                VISITED_METHODS.add(element)
            }
        }

    }

}
