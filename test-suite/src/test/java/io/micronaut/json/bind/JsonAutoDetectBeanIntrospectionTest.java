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
package io.micronaut.json.bind;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWriteProperty;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonAutoDetectBeanIntrospectionTest {

    @Test
    void jsonAutoDetectFieldVisibilityIsRecognizedAsBeanProperties() throws IOException {
        BeanIntrospection<FieldVisibilityBean> introspection = BeanIntrospection.getIntrospection(FieldVisibilityBean.class);

        assertEquals(
            Set.of("privateField", "packageField", "protectedField", "publicField", "fieldWithAccessors"),
            propertyNames(introspection)
        );

        FieldVisibilityBean bean = new FieldVisibilityBean();
        BeanProperty<FieldVisibilityBean, String> property =
            introspection.getRequiredProperty("fieldWithAccessors", String.class);

        property.set(bean, "field");
        assertEquals("field", bean.fieldWithAccessors);
        assertEquals("field", property.get(bean));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode json = objectMapper.valueToTree(bean);
        assertEquals("private-field", json.get("privateField").asString());
        assertEquals("package-field", json.get("packageField").asString());
        assertEquals("protected-field", json.get("protectedField").asString());
        assertEquals("public-field", json.get("publicField").asString());
        assertEquals("field", json.get("fieldWithAccessors").asString());

        FieldVisibilityBean deserialized = objectMapper.readValue(
            """
            {
              "privateField": "private-json",
              "packageField": "package-json",
              "protectedField": "protected-json",
              "publicField": "public-json",
              "fieldWithAccessors": "field-json"
            }
            """,
            FieldVisibilityBean.class
        );
        assertEquals("private-json", deserialized.privateField);
        assertEquals("package-json", deserialized.packageField);
        assertEquals("protected-json", deserialized.protectedField);
        assertEquals("public-json", deserialized.publicField);
        assertEquals("field-json", deserialized.fieldWithAccessors);
    }

    @Test
    void jsonAutoDetectMethodVisibilityIsRecognizedAsBeanProperties() throws IOException {
        BeanIntrospection<MethodVisibilityBean> introspection = BeanIntrospection.getIntrospection(MethodVisibilityBean.class);

        assertEquals(Set.of("privateGetter", "privateSetter", "active", "name"), propertyNames(introspection));

        MethodVisibilityBean bean = new MethodVisibilityBean();
        BeanProperty<MethodVisibilityBean, String> getterProperty =
            introspection.getRequiredProperty("privateGetter", String.class);
        assertTrue(getterProperty.isReadOnly());
        assertEquals("private-getter-getter", getterProperty.get(bean));
        assertThrows(UnsupportedOperationException.class, () -> getterProperty.set(bean, "getter"));

        BeanWriteProperty<MethodVisibilityBean, String> setterProperty =
            introspection.getRequiredWriteProperty("privateSetter", String.class);
        setterProperty.set(bean, "setter");
        assertEquals("setter-setter", bean.privateSetter);

        BeanProperty<MethodVisibilityBean, String> nameProperty =
            introspection.getRequiredProperty("name", String.class);
        assertTrue(nameProperty.isWriteOnly());
        nameProperty.set(bean, "name");
        assertEquals("name-setter", bean.name);
        assertThrows(UnsupportedOperationException.class, () -> nameProperty.get(bean));

        BeanProperty<MethodVisibilityBean, Boolean> activeProperty =
            introspection.getRequiredProperty("active", boolean.class);
        activeProperty.set(bean, true);
        assertTrue(activeProperty.get(bean));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode json = objectMapper.valueToTree(bean);
        assertEquals("private-getter-getter", json.get("privateGetter").asString());
        assertTrue(json.get("active").asBoolean());
        assertFalse(json.has("privateSetter"));
        assertFalse(json.has("name"));

        MethodVisibilityBean deserialized = objectMapper.readValue(
            """
            {
              "privateSetter": "json",
              "name": "json-name",
              "active": true
            }
            """,
            MethodVisibilityBean.class
        );
        assertEquals("json-setter", deserialized.privateSetter);
        assertEquals("json-name-setter", deserialized.name);
        assertTrue(deserialized.active);
    }

    @Test
    void jsonAutoDetectPublicOnlyVisibilityRestrictsBeanProperties() throws IOException {
        BeanIntrospection<PublicOnlyVisibilityBean> introspection =
            BeanIntrospection.getIntrospection(PublicOnlyVisibilityBean.class);

        assertEquals(Set.of("publicField", "publicGetter", "publicSetter"), propertyNames(introspection));

        PublicOnlyVisibilityBean bean = new PublicOnlyVisibilityBean();
        BeanWriteProperty<PublicOnlyVisibilityBean, String> setterProperty =
            introspection.getRequiredWriteProperty("publicSetter", String.class);
        setterProperty.set(bean, "setter");
        assertEquals("setter-setter", bean.publicSetter);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode json = objectMapper.valueToTree(bean);
        assertEquals("public-field", json.get("publicField").asString());
        assertEquals("public-getter-getter", json.get("publicGetter").asString());
        assertFalse(json.has("active"));
        assertFalse(json.has("protectedGetter"));
        assertFalse(json.has("publicSetter"));

        PublicOnlyVisibilityBean deserialized = objectMapper.readValue(
            """
            {
              "publicField": "public-json",
              "publicSetter": "public-json",
              "protectedSetter": "protected-json"
            }
            """,
            PublicOnlyVisibilityBean.class
        );
        assertEquals("public-json", deserialized.publicField);
        assertEquals("public-json-setter", deserialized.publicSetter);
        assertEquals("protected-setter", deserialized.protectedSetter);
    }

    private static Set<String> propertyNames(BeanIntrospection<?> introspection) {
        return introspection.getBeanProperties()
            .stream()
            .map(BeanProperty::getName)
            .collect(Collectors.toSet());
    }

    @Introspected
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE
    )
    static final class FieldVisibilityBean {
        private String privateField = "private-field";
        String packageField = "package-field";
        protected String protectedField = "protected-field";
        public String publicField = "public-field";
        private String fieldWithAccessors = "field-with-accessors";

        public String getFieldWithAccessors() {
            return fieldWithAccessors + "-getter";
        }

        public void setFieldWithAccessors(String fieldWithAccessors) {
            this.fieldWithAccessors = fieldWithAccessors + "-setter";
        }
    }

    @Introspected
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.ANY,
        isGetterVisibility = JsonAutoDetect.Visibility.ANY,
        setterVisibility = JsonAutoDetect.Visibility.ANY
    )
    static final class MethodVisibilityBean {
        private String privateGetter = "private-getter";
        private String privateSetter;
        private String name;
        private boolean active;

        private String getPrivateGetter() {
            return privateGetter + "-getter";
        }

        private void setPrivateSetter(String privateSetter) {
            this.privateSetter = privateSetter + "-setter";
        }

        private String isName() {
            return name + "-is-getter";
        }

        private void setName(String name) {
            this.name = name + "-setter";
        }

        private boolean isActive() {
            return active;
        }

        private void setActive(boolean active) {
            this.active = active;
        }
    }

    @Introspected
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY
    )
    static final class PublicOnlyVisibilityBean {
        public String publicField = "public-field";
        String packageField = "package-field";
        protected String protectedField = "protected-field";
        private String privateField = "private-field";
        private String publicGetter = "public-getter";
        private String protectedGetter = "protected-getter";
        private boolean active = true;
        private String publicSetter;
        private String protectedSetter = "protected-setter";

        public String getPublicGetter() {
            return publicGetter + "-getter";
        }

        protected String getProtectedGetter() {
            return protectedGetter + "-getter";
        }

        public boolean isActive() {
            return active;
        }

        public void setPublicSetter(String publicSetter) {
            this.publicSetter = publicSetter + "-setter";
        }

        protected void setProtectedSetter(String protectedSetter) {
            this.protectedSetter = protectedSetter + "-setter";
        }
    }
}
