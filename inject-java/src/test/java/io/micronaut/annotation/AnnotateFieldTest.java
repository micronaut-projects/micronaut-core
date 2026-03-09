package io.micronaut.annotation;

import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of AnnotateFieldSpec.
 */
class AnnotateFieldTest extends AbstractTypeElementTest {

    @Override
    protected Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return List.of(new AnnotationFieldVisitor());
    }

    @Test
    void testAnnotating() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotationFieldClass", """
            package addann;

            import io.micronaut.context.annotation.Bean;
            import jakarta.inject.Inject;

            @Bean
            class AnnotationFieldClass {

                @Inject
                public MyBean1 myField1;

                @Inject
                public MyBean1 myField2;

            }

            class MyBean1 {
            }
            """);

        // Use names instead of relying on index ordering
        var fields = new ArrayList<>(definition.getInjectedFields());
        var myField1 = fields.stream().filter(f -> f.getName().equals("myField1")).findFirst().orElseThrow();
        var myField2 = fields.stream().filter(f -> f.getName().equals("myField2")).findFirst().orElseThrow();

        assertEquals("myField1", myField1.getName());
        assertTrue(myField1.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation.class), "myField1 should be annotated with @MyAnnotation");

        assertEquals("myField2", myField2.getName());
        assertFalse(myField2.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation.class), "myField2 should NOT be annotated with @MyAnnotation");
    }

    static final class AnnotationFieldVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        public void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName().equals("AnnotationFieldClass")) {
                var myField1 = element.findField("myField1").orElseThrow();
                assertEquals(List.of(AnnotationUtil.INJECT), new ArrayList<>(myField1.getAnnotationMetadata().getAnnotationNames()));
                myField1.annotate(MyAnnotation.class);
                assertEquals(List.of(AnnotationUtil.INJECT, MyAnnotation.class.getName()), new ArrayList<>(myField1.getAnnotationMetadata().getAnnotationNames()));
                // Type-level metadata should remain empty
                assertTrue(myField1.getType().getAnnotationNames().isEmpty());
                assertTrue(myField1.getType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(myField1.getType().getType().getAnnotationMetadata().isEmpty());
                assertTrue(myField1.getGenericType().getAnnotationNames().isEmpty());
                assertTrue(myField1.getGenericType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(myField1.getGenericType().getType().getAnnotationMetadata().isEmpty());

                // Validate the cache is working
                var cached = new ArrayList<>(context.getClassElement("addann.AnnotationFieldClass").orElseThrow()
                    .findField("myField1").orElseThrow()
                    .getAnnotationMetadata().getAnnotationNames());
                assertEquals(List.of(AnnotationUtil.INJECT, MyAnnotation.class.getName()), cached);

                // Second field should not inherit annotations
                var myField2 = element.findField("myField2").orElseThrow();
                assertEquals(List.of(AnnotationUtil.INJECT), new ArrayList<>(myField2.getAnnotationMetadata().getAnnotationNames()));
                assertTrue(myField2.getType().getAnnotationNames().isEmpty());
                assertTrue(myField2.getType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(myField2.getGenericType().getAnnotationNames().isEmpty());
                assertTrue(myField2.getGenericType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty());

                var cached2 = new ArrayList<>(context.getClassElement("addann.AnnotationFieldClass").orElseThrow()
                    .findField("myField2").orElseThrow()
                    .getAnnotationMetadata().getAnnotationNames());
                assertEquals(List.of(AnnotationUtil.INJECT), cached2);

                // The referenced type should not receive annotations either
                assertTrue(context.getClassElement("addann.MyBean1").orElseThrow().getAnnotationMetadata().isEmpty());
            }
        }
    }
}
