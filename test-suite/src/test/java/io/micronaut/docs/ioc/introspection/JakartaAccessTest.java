/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.docs.ioc.introspection;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JakartaAccessTest {

    @Test
    void testJakartaPersistenceFieldAccessAttributeIsRecognizedAsBeanProperty() {
        BeanIntrospection<JpaPropertyAccessEntity> introspection = BeanIntrospection.getIntrospection(JpaPropertyAccessEntity.class);
        BeanProperty<JpaPropertyAccessEntity, String> property = introspection.getRequiredProperty("fieldAccess", String.class);
        JpaPropertyAccessEntity entity = new JpaPropertyAccessEntity();

        assertMappedAccess(property, AccessType.FIELD);

        property.set(entity, "field");
        assertEquals("field", entity.fieldAccess);
        assertEquals("field", property.get(entity));
    }

    @Test
    void testJakartaPersistencePropertyAccessAttributeIsRecognizedAsBeanProperty() {
        BeanIntrospection<JpaFieldAccessEntity> introspection = BeanIntrospection.getIntrospection(JpaFieldAccessEntity.class);
        BeanProperty<JpaFieldAccessEntity, String> property = introspection.getRequiredProperty("propertyAccess", String.class);
        JpaFieldAccessEntity entity = new JpaFieldAccessEntity();

        assertMappedAccess(property, AccessType.PROPERTY);

        property.set(entity, "property");
        assertEquals("property-setter", entity.propertyAccess);
        assertEquals("property-setter-getter", property.get(entity));
    }

    private static void assertMappedAccess(BeanProperty<?, ?> property, AccessType accessType) {
        assertEquals(accessType, property.enumValue(Access.class, AccessType.class).orElseThrow());
        assertTrue(property.hasAnnotation(Introspected.Property.class));
        assertTrue(property.booleanValue(Introspected.Property.class, "ignoreOtherAccessors").orElseThrow());
    }

    @Entity
    @Access(AccessType.PROPERTY)
    static final class JpaPropertyAccessEntity {
        private Long id;

        @Access(AccessType.FIELD)
        String fieldAccess;

        @Id
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getFieldAccess() {
            return fieldAccess + "-getter";
        }

        public void setFieldAccess(String fieldAccess) {
            this.fieldAccess = fieldAccess + "-setter";
        }
    }

    @Entity
    @Access(AccessType.FIELD)
    static final class JpaFieldAccessEntity {
        @Id
        Long id;

        @Transient
        private String propertyAccess;

        @Access(AccessType.PROPERTY)
        public String getPropertyAccess() {
            return propertyAccess + "-getter";
        }

        public void setPropertyAccess(String propertyAccess) {
            this.propertyAccess = propertyAccess + "-setter";
        }
    }
}
