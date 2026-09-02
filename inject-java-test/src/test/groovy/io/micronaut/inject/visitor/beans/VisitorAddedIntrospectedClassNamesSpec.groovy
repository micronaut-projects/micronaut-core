package io.micronaut.inject.visitor.beans

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.validation.visitor.ValidationVisitor
import org.jspecify.annotations.NonNull

import javax.annotation.processing.SupportedAnnotationTypes

/**
 * A {@link TypeElementVisitor} can make an otherwise unannotated nested type introspectable by
 * naming it on the {@link Introspected} annotation it adds to the enclosing type. The nested type
 * is never handed to the processor itself, so the introspection is generated on behalf of the
 * enclosing type and carries its name.
 */
class VisitorAddedIntrospectedClassNamesSpec extends AbstractTypeElementSpec {

    void "test a nested type named on classNames added by a visitor is introspected once"() {
        given:
        ApplicationContext context = buildContext('test.Outer', '''
package test;

class Outer {
    static class Nested {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}

class Other {
    static class OtherNested {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
''')

        when:
        def reference = context.classLoader.loadClass('test.$test_Outer$Nested$Introspection').newInstance()

        then:
        reference instanceof BeanIntrospectionReference
        reference.load().beanType.name == 'test.Outer$Nested'
        introspectionsOf(context, 'test.Outer$Nested').size() == 1
        introspectionsOf(context, 'test.Other$OtherNested').size() == 1

        cleanup:
        context?.close()
    }

    void "test a nested type named by a visitor and by a source declared importer is introspected once"() {
        given:
        ApplicationContext context = buildContext('test.Holder', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(classNames = {"test.Outer$Nested"})
class Holder {}

class Outer {
    static class Nested {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
''')

        expect:
        introspectionsOf(context, 'test.Outer$Nested').size() == 1

        cleanup:
        context?.close()
    }

    private static List<String> introspectionsOf(ApplicationContext context, String beanTypeName) {
        return context.classLoader
                .getResources("META-INF/micronaut/io.micronaut.core.beans.BeanIntrospectionReference/")
                .collect { it.toString().substring(it.toString().lastIndexOf('/') + 1) }
                .findAll { it.startsWith('test.') }
                .findAll { context.classLoader.loadClass(it).newInstance().beanType.name == beanTypeName }
    }

    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new MyTypeElementVisitorProcessor()
            }
        }
    }

    @SupportedAnnotationTypes("*")
    static class MyTypeElementVisitorProcessor extends TypeElementVisitorProcessor {
        @NonNull
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new IntrospectNestedVisitor(), new ValidationVisitor(), new IntrospectedTypeElementVisitor()]
        }
    }

    /**
     * Runs before the introspection visitor and names every unannotated static nested type on the
     * {@link Introspected} annotation it adds to the enclosing type.
     */
    static class IntrospectNestedVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        int getOrder() {
            return 88
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (!element.getName().startsWith('test.')) {
                return
            }
            String[] unannotatedNestedTypes = element.getEnclosedElements(ElementQuery.ALL_INNER_CLASSES.onlyDeclared())
                    .stream()
                    .filter(nested -> nested.isStatic() && !nested.isInterface() && !nested.isEnum() && !nested.isRecord())
                    .filter(nested -> nested.getAnnotationMetadata().getAnnotationNames().isEmpty())
                    .map(ClassElement::getName)
                    .toArray(String[]::new)
            element.annotate(Introspected.class, builder -> {
                builder.member("accessKind", Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD)
                builder.member("visibility", Introspected.Visibility.ANY)
                if (unannotatedNestedTypes.length > 0) {
                    builder.member("classNames", unannotatedNestedTypes)
                }
            })
        }
    }
}
