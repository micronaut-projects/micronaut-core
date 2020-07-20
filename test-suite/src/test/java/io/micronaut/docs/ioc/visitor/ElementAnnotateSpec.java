/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
