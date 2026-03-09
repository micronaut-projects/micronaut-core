package io.micronaut.annotation;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of AnnotateClassSpec.
 */
class AnnotateClassTest extends AbstractTypeElementTest {

    @Override
    protected Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return List.of(new AnnotateClassVisitor());
    }

    @Test
    void testAnnotating1() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;

            class AnnotateClass {

                @Executable
                public String myMethod1() {
                    return null;
                }
            }
            """);
        assertTrue(definition.hasAnnotation(MyAnnotation.class));
    }

    @Test
    void testAnnotating2() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.Foobar1$AnnotateClass", """
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
            """);
        assertTrue(definition.hasAnnotation(MyAnnotation.class));
    }

    @Test
    void testAnnotating3() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.Foobar2$AnnotateClass", """
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
            """);
        assertTrue(definition.hasAnnotation(MyAnnotation.class));
    }

    @Test
    void testAnnotating4() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.Foobar3$AnnotateClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import org.jspecify.annotations.Nullable;
            import io.micronaut.core.annotation.ReflectiveAccess;

            class Foobar3 {

                static class AnnotateClass {

                    @Executable
                    @ReflectiveAccess
                    private String myMethod2() {
                        return null;
                    }
                }
            }
            """);
        assertTrue(definition.hasAnnotation(MyAnnotation.class));
    }

    @Test
    void testAnnotating5() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.Foobar4$AnnotateClass", """
            package addann;

            import io.micronaut.context.annotation.Property;

            class Foobar4 {

                static class AnnotateClass {

                    @Property(name = "xyz") // Make the BeanDefinitionInjectProcessor to see the class
                    private String myField;
                }
            }
            """);
        assertTrue(definition.hasAnnotation(MyAnnotation.class));
    }

    static final class AnnotateClassVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        public void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName().endsWith("AnnotateClass")) {
                element.annotate(MyAnnotation.class);
                element.annotate(Prototype.class);

                // Validate the cache is working
                ClassElement newClassElement = context.getClassElement(element.getName()).orElseThrow();
                assertTrue(newClassElement.hasAnnotation(MyAnnotation.class));
                assertTrue(newClassElement.hasAnnotation(Prototype.class));
            }
        }
    }
}
