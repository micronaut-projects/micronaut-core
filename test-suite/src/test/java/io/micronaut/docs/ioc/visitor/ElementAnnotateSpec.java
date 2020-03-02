package io.micronaut.docs.ioc.visitor;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.docs.client.versioning.HelloClient;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import example.micronaut.inject.visitor.AnnotatingVisitor;
import junit.framework.TestCase;

public class ElementAnnotateSpec extends TestCase {

    public void testThatIntrospectionsCanBeDynamicallyAnnotated() {
        final BeanIntrospection<VersionedIntrospected> introspection = BeanIntrospection.getIntrospection(VersionedIntrospected.class);


        assertTrue(
                introspection.hasAnnotation(AnnotatingVisitor.ANN_NAME)
        );

        assertEquals(
                "class",
                introspection.getValue(AnnotatingVisitor.ANN_NAME, String.class).orElse(null)
                );

//        TODO: Following case does not yet work, not sure if needed
//        final BeanProperty<VersionedIntrospected, String> prop = introspection.getRequiredProperty("foo", String.class);
//
//        assertTrue(prop.hasAnnotation(AnnotatingVisitor.ANN_NAME));
//        assertEquals(
//                "field",
//                prop.getValue(AnnotatingVisitor.ANN_NAME, String.class).orElse(null)
//        );
    }

    public void testThatAopAdviceWasAnnotated() {
        try (ApplicationContext context = ApplicationContext.run()) {

            final BeanDefinition<HelloClient> definition = context.getBeanDefinition(HelloClient.class);

            assertTrue(definition.hasAnnotation(AnnotatingVisitor.ANN_NAME));
            assertEquals("class", definition.getValue(AnnotatingVisitor.ANN_NAME, String.class).orElse(null));
            final ExecutableMethod<HelloClient, Object> m = definition.findMethod("sayHello", String.class).orElse(null);

            assertNotNull(m);

            assertTrue(m.hasDeclaredAnnotation(AnnotatingVisitor.ANN_NAME));
            assertEquals("method", m.getValue(AnnotatingVisitor.ANN_NAME, String.class).orElse(null));
        }
    }
}
