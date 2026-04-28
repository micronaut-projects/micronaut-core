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

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanReadProperty;
import io.micronaut.core.beans.BeanWriteProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.http.hateoas.GenericResource;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonPropertyBeanIntrospectionTest {

    private static final List<JsonPropertyMember> JSON_PROPERTY_MEMBERS = List.of(
        new JsonPropertyMember("fieldProperty", "field_property", "field", true, true),
        new JsonPropertyMember("readOnlyFieldProperty", "read_only_field_property", "read-only-field", true, false),
        new JsonPropertyMember("writeOnlyFieldProperty", "write_only_field_property", "write-only-field", false, true),
        new JsonPropertyMember("pairedProperty", "paired_property", "paired", true, true),
        new JsonPropertyMember("readOnlyProperty", "read_only_property", "read-only", true, false),
        new JsonPropertyMember("writeOnlyProperty", "write_only_property", "write-only", false, true),
        new JsonPropertyMember("readOnlyAccessorProperty", "read_only_accessor_property", "read-only-accessor", true, false),
        new JsonPropertyMember("writeOnlyAccessorProperty", "write_only_accessor_property", "write-only-accessor", false, true)
    );
    private static final List<JsonPropertyBooleanMember> JSON_PROPERTY_BOOLEAN_MEMBERS = List.of(
        new JsonPropertyBooleanMember("active", "active_flag", true),
        new JsonPropertyBooleanMember("visible", "visible_flag", false)
    );
    private static final List<JsonPropertyBooleanSetterWithoutParameter> JSON_PROPERTY_BOOLEAN_SETTERS_WITHOUT_PARAMETERS = List.of(
        new JsonPropertyBooleanSetterWithoutParameter("debug", "debug_flag"),
        new JsonPropertyBooleanSetterWithoutParameter("methodFlag", "method_flag")
    );
    private static final List<JsonPropertyAccessorMember> JSON_PROPERTY_ACCESSOR_MEMBERS = List.of(
        new JsonPropertyAccessorMember(
            "fieldWithAccessors",
            "field_with_accessors",
            "field-with-accessors",
            true,
            true,
            "field-with-accessors-setter",
            "field-with-accessors-setter-getter"
        ),
        new JsonPropertyAccessorMember(
            "readOnlyFieldWithAccessors",
            "read_only_field_with_accessors",
            "read-only-field-with-accessors",
            true,
            false,
            "read-only-field-with-accessors",
            "read-only-field-with-accessors-getter"
        ),
        new JsonPropertyAccessorMember(
            "writeOnlyFieldWithAccessors",
            "write_only_field_with_accessors",
            "write-only-field-with-accessors",
            false,
            true,
            "write-only-field-with-accessors-setter",
            "write-only-field-with-accessors-setter-getter"
        )
    );
    private static final List<JsonGetterSetterMember> JSON_GETTER_SETTER_MEMBERS = List.of(
        new JsonGetterSetterMember("readWriteAccessor", "read_write_accessor", "read-write", true, true),
        new JsonGetterSetterMember("getterWithImplicitSetter", "getter_with_implicit_setter", "getter-with-implicit-setter", true, true),
        new JsonGetterSetterMember("setterWithImplicitGetter", "setter_with_implicit_getter", "setter-with-implicit-getter", true, true),
        new JsonGetterSetterMember("readOnlyAccessor", "read_only_accessor", "read-only", true, false),
        new JsonGetterSetterMember("writeOnlyAccessor", "write_only_accessor", "write-only", false, true),
        new JsonGetterSetterMember("fluentAccessor", "fluent_accessor", "fluent", true, true)
    );
    private static final List<IntrospectedPropertyMember> INTROSPECTED_PROPERTY_MEMBERS = List.of(
        new IntrospectedPropertyMember("valueAccessor", "value_accessor", "value"),
        new IntrospectedPropertyMember("namedAccessor", "named_accessor", "named")
    );

    private static final String ISSUE_691_MESSAGE = "Internal Server Error";
    private static final String ISSUE_691_EMBEDDED_MESSAGE = "Internal Server Error: Something bad happened";
    private static final String ISSUE_691_LINK = "/resolve";
    private static final Map<String, Object> ISSUE_691_LINKS = Map.of(
        Link.SELF.toString(),
        List.of(Map.of(
            Link.HREF.toString(), ISSUE_691_LINK,
            "templated", false
        ))
    );
    private static final Map<String, List<Resource>> ISSUE_691_EMBEDDED = Map.of(
        "errors",
        List.of(new JsonError(ISSUE_691_EMBEDDED_MESSAGE))
    );
    private static final String ISSUE_691_JSON = """
        {
          "_links": {
            "self": [
              {
                "href": "/resolve",
                "templated": false
              }
            ]
          },
          "_embedded": {
            "errors": [
              {
                "message": "Internal Server Error: Something bad happened"
              }
            ]
          },
          "message": "Internal Server Error"
        }
        """;
    private static final List<HateoasProperty> HATEOAS_PROPERTIES = List.of(
        new HateoasProperty("links", Resource.LINKS, ISSUE_691_LINKS, OptionalMultiValues.class, Map.class),
        new HateoasProperty("embedded", Resource.EMBEDDED, ISSUE_691_EMBEDDED, OptionalMultiValues.class, Map.class)
    );

    @Test
    void jsonPropertyMembersAreRecognizedAsBeanProperties() {
        BeanIntrospection<JsonPropertyBean> introspection = BeanIntrospection.getIntrospection(JsonPropertyBean.class);

        assertEquals(
            JSON_PROPERTY_MEMBERS
                .stream()
                .map(JsonPropertyMember::beanPropertyName)
                .collect(Collectors.toSet()),
            introspection.getBeanProperties()
                .stream()
                .map(BeanProperty::getName)
                .collect(Collectors.toSet())
        );

        JsonPropertyBean bean = new JsonPropertyBean();

        for (JsonPropertyMember member : JSON_PROPERTY_MEMBERS) {
            BeanProperty<JsonPropertyBean, String> property =
                introspection.getRequiredProperty(member.beanPropertyName(), String.class);
            assertEquals(
                member.jsonPropertyName(),
                property.stringValue(JsonProperty.class).orElseThrow(),
                member.beanPropertyName()
            );
            assertEquals(
                member.jsonPropertyName(),
                property.stringValue(Introspected.Property.class, "name").orElseThrow(),
                member.beanPropertyName()
            );
            assertEquals(
                member.accessKinds(),
                List.of(property.enumValues(
                    Introspected.Property.class,
                    "accessKind",
                    Introspected.Property.Access.class
                )),
                member.beanPropertyName()
            );
            assertEquals(!member.writable(), property.isReadOnly(), member.beanPropertyName());
            assertEquals(!member.readable(), property.isWriteOnly(), member.beanPropertyName());

            if (member.writable()) {
                BeanWriteProperty<JsonPropertyBean, String> writeProperty =
                    introspection.getRequiredWriteProperty(member.beanPropertyName(), String.class);
                writeProperty.set(bean, member.value());
                assertEquals(member.value(), readBeanProperty(bean, member), member.beanPropertyName());
            }

            if (member.readable()) {
                BeanReadProperty<JsonPropertyBean, String> readProperty =
                    introspection.getRequiredReadProperty(member.beanPropertyName(), String.class);
                assertEquals(member.value(), readProperty.get(bean), member.beanPropertyName());
                assertEquals(member.value(), property.get(bean), member.beanPropertyName());
            } else {
                assertThrows(UnsupportedOperationException.class, () -> property.get(bean), member.beanPropertyName());
            }
        }
    }

    @Test
    void jacksonDatabindRecognizesJsonPropertyMembersAsBeanProperties() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonPropertyBean bean = new JsonPropertyBean();
        JSON_PROPERTY_MEMBERS.stream()
            .filter(JsonPropertyMember::writable)
            .forEach(member -> writeBeanProperty(bean, member));

        JsonNode json = objectMapper.valueToTree(bean);

        for (JsonPropertyMember member : JSON_PROPERTY_MEMBERS) {
            if (member.readable()) {
                assertEquals(
                    member.value(),
                    json.get(member.jsonPropertyName()).asString(),
                    member.beanPropertyName()
                );
            } else {
                assertFalse(json.has(member.jsonPropertyName()), member.beanPropertyName());
            }
        }

        JsonPropertyBean deserialized = objectMapper.readValue(
            objectMapper.writeValueAsString(writableJsonProperties()),
            JsonPropertyBean.class
        );

        for (JsonPropertyMember member : JSON_PROPERTY_MEMBERS) {
            if (member.writable()) {
                assertEquals(member.value(), readBeanProperty(deserialized, member), member.beanPropertyName());
            }
        }
    }

    @Test
    void jsonPropertyBooleanMembersAreRecognizedAsBeanProperties() {
        BeanIntrospection<JsonPropertyBooleanBean> introspection = BeanIntrospection.getIntrospection(JsonPropertyBooleanBean.class);

        assertEquals(
            Stream.concat(
                    JSON_PROPERTY_BOOLEAN_MEMBERS
                        .stream()
                        .map(JsonPropertyBooleanMember::beanPropertyName),
                    JSON_PROPERTY_BOOLEAN_SETTERS_WITHOUT_PARAMETERS
                        .stream()
                        .map(JsonPropertyBooleanSetterWithoutParameter::beanPropertyName)
                )
                .collect(Collectors.toSet()),
            introspection.getBeanProperties()
                .stream()
                .map(BeanProperty::getName)
                .collect(Collectors.toSet())
        );

        JsonPropertyBooleanBean bean = new JsonPropertyBooleanBean();

        for (JsonPropertyBooleanMember member : JSON_PROPERTY_BOOLEAN_MEMBERS) {
            BeanProperty<JsonPropertyBooleanBean, Boolean> property =
                introspection.getRequiredProperty(member.beanPropertyName(), boolean.class);
            assertReadWriteJsonProperty(property, member.beanPropertyName(), member.jsonPropertyName());

            BeanWriteProperty<JsonPropertyBooleanBean, Boolean> writeProperty =
                introspection.getRequiredWriteProperty(member.beanPropertyName(), boolean.class);
            writeProperty.set(bean, member.value());
            assertEquals(member.value(), readBooleanBeanProperty(bean, member), member.beanPropertyName());

            BeanReadProperty<JsonPropertyBooleanBean, Boolean> readProperty =
                introspection.getRequiredReadProperty(member.beanPropertyName(), boolean.class);
            assertEquals(member.value(), readProperty.get(bean), member.beanPropertyName());
            assertEquals(member.value(), property.get(bean), member.beanPropertyName());
        }

        for (JsonPropertyBooleanSetterWithoutParameter member : JSON_PROPERTY_BOOLEAN_SETTERS_WITHOUT_PARAMETERS) {
            BeanProperty<JsonPropertyBooleanBean, Boolean> property =
                introspection.getRequiredProperty(member.beanPropertyName(), boolean.class);
            assertReadWriteJsonProperty(property, member.beanPropertyName(), member.jsonPropertyName());
            assertEquals(false, property.get(bean), member.beanPropertyName());

            BeanWriteProperty<JsonPropertyBooleanBean, Boolean> writeProperty =
                introspection.getRequiredWriteProperty(member.beanPropertyName(), boolean.class);
            writeProperty.set(bean, false);
            assertEquals(true, property.get(bean), member.beanPropertyName());

            BeanReadProperty<JsonPropertyBooleanBean, Boolean> readProperty =
                introspection.getRequiredReadProperty(member.beanPropertyName(), boolean.class);
            assertEquals(true, readProperty.get(bean), member.beanPropertyName());
        }
    }

    @Test
    void jacksonDatabindRecognizesJsonPropertyBooleanMembersAsBeanProperties() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonPropertyBooleanBean bean = new JsonPropertyBooleanBean();
        JSON_PROPERTY_BOOLEAN_MEMBERS.forEach(member -> writeBooleanBeanProperty(bean, member));

        JsonNode json = objectMapper.valueToTree(bean);

        for (JsonPropertyBooleanMember member : JSON_PROPERTY_BOOLEAN_MEMBERS) {
            assertEquals(
                member.value(),
                json.get(member.jsonPropertyName()).asBoolean(),
                member.beanPropertyName()
            );
        }

        JsonPropertyBooleanBean deserialized = objectMapper.readValue(
            objectMapper.writeValueAsString(writableJsonBooleanProperties()),
            JsonPropertyBooleanBean.class
        );

        for (JsonPropertyBooleanMember member : JSON_PROPERTY_BOOLEAN_MEMBERS) {
            assertEquals(member.value(), readBooleanBeanProperty(deserialized, member), member.beanPropertyName());
        }
    }

    @Test
    void jsonPropertyAnnotatedFieldsUseBeanAccessorsWhenPresent() {
        BeanIntrospection<JsonPropertyAccessorBean> introspection = BeanIntrospection.getIntrospection(JsonPropertyAccessorBean.class);

        assertEquals(
            JSON_PROPERTY_ACCESSOR_MEMBERS
                .stream()
                .map(JsonPropertyAccessorMember::beanPropertyName)
                .collect(Collectors.toSet()),
            introspection.getBeanProperties()
                .stream()
                .map(BeanProperty::getName)
                .collect(Collectors.toSet())
        );

        JsonPropertyAccessorBean bean = new JsonPropertyAccessorBean();

        for (JsonPropertyAccessorMember member : JSON_PROPERTY_ACCESSOR_MEMBERS) {
            BeanProperty<JsonPropertyAccessorBean, String> property =
                introspection.getRequiredProperty(member.beanPropertyName(), String.class);
            assertEquals(
                member.jsonPropertyName(),
                property.stringValue(JsonProperty.class).orElseThrow(),
                member.beanPropertyName()
            );
            assertEquals(
                member.jsonPropertyName(),
                property.stringValue(Introspected.Property.class, "name").orElseThrow(),
                member.beanPropertyName()
            );
            assertFalse(
                property.booleanValue(Introspected.Property.class, "ignoreOtherAccessors").orElse(false),
                member.beanPropertyName()
            );
            assertEquals(!member.writable(), property.isReadOnly(), member.beanPropertyName());
            assertEquals(!member.readable(), property.isWriteOnly(), member.beanPropertyName());

            if (member.writable()) {
                BeanWriteProperty<JsonPropertyAccessorBean, String> writeProperty =
                    introspection.getRequiredWriteProperty(member.beanPropertyName(), String.class);
                writeProperty.set(bean, member.value());
                assertEquals(member.storedValue(), readAccessorBeanField(bean, member), member.beanPropertyName());
            } else {
                assertThrows(UnsupportedOperationException.class, () -> property.set(bean, member.value()), member.beanPropertyName());
            }

            if (member.readable()) {
                BeanReadProperty<JsonPropertyAccessorBean, String> readProperty =
                    introspection.getRequiredReadProperty(member.beanPropertyName(), String.class);
                assertEquals(member.readValue(), readProperty.get(bean), member.beanPropertyName());
                assertEquals(member.readValue(), property.get(bean), member.beanPropertyName());
            } else {
                assertThrows(UnsupportedOperationException.class, () -> property.get(bean), member.beanPropertyName());
            }
        }
    }

    @Test
    void jacksonDatabindUsesBeanAccessorsForJsonPropertyAnnotatedFieldsWhenPresent() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonPropertyAccessorBean bean = new JsonPropertyAccessorBean();
        JSON_PROPERTY_ACCESSOR_MEMBERS.stream()
            .filter(JsonPropertyAccessorMember::writable)
            .forEach(member -> writeAccessorBeanProperty(bean, member));

        JsonNode json = objectMapper.valueToTree(bean);

        for (JsonPropertyAccessorMember member : JSON_PROPERTY_ACCESSOR_MEMBERS) {
            if (member.readable()) {
                assertEquals(
                    member.readValue(),
                    json.get(member.jsonPropertyName()).asString(),
                    member.beanPropertyName()
                );
            } else {
                assertFalse(json.has(member.jsonPropertyName()), member.beanPropertyName());
            }
        }

        JsonPropertyAccessorBean deserialized = objectMapper.readValue(
            objectMapper.writeValueAsString(allJsonAccessorProperties()),
            JsonPropertyAccessorBean.class
        );

        for (JsonPropertyAccessorMember member : JSON_PROPERTY_ACCESSOR_MEMBERS) {
            assertEquals(member.storedValue(), readAccessorBeanField(deserialized, member), member.beanPropertyName());
        }
    }

    @Test
    void jsonGetterAndJsonSetterMembersAreRecognizedAsBeanProperties() {
        BeanIntrospection<JsonGetterSetterBean> introspection = BeanIntrospection.getIntrospection(JsonGetterSetterBean.class);

        assertEquals(
            JSON_GETTER_SETTER_MEMBERS
                .stream()
                .map(JsonGetterSetterMember::beanPropertyName)
                .collect(Collectors.toSet()),
            introspection.getBeanProperties()
                .stream()
                .map(BeanProperty::getName)
                .collect(Collectors.toSet())
        );

        JsonGetterSetterBean bean = new JsonGetterSetterBean();

        for (JsonGetterSetterMember member : JSON_GETTER_SETTER_MEMBERS) {
            BeanProperty<JsonGetterSetterBean, String> property =
                introspection.getRequiredProperty(member.beanPropertyName(), String.class);
            assertEquals(
                member.jsonPropertyName(),
                property.stringValue(Introspected.Property.class, "name").orElseThrow(),
                member.beanPropertyName()
            );
            assertEquals(!member.writable(), property.isReadOnly(), member.beanPropertyName());
            assertEquals(!member.readable(), property.isWriteOnly(), member.beanPropertyName());

            if (member.writable()) {
                BeanWriteProperty<JsonGetterSetterBean, String> writeProperty =
                    introspection.getRequiredWriteProperty(member.beanPropertyName(), String.class);
                writeProperty.set(bean, member.value());
                assertEquals(member.value(), readJsonGetterSetterBeanProperty(bean, member), member.beanPropertyName());
            }

            if (member.readable()) {
                BeanReadProperty<JsonGetterSetterBean, String> readProperty =
                    introspection.getRequiredReadProperty(member.beanPropertyName(), String.class);
                assertEquals(member.value(), readProperty.get(bean), member.beanPropertyName());
                assertEquals(member.value(), property.get(bean), member.beanPropertyName());
            } else {
                assertThrows(UnsupportedOperationException.class, () -> property.get(bean), member.beanPropertyName());
            }
        }
    }

    @Test
    void jacksonDatabindRecognizesJsonGetterAndJsonSetterMembersAsBeanProperties() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonGetterSetterBean bean = new JsonGetterSetterBean();
        JSON_GETTER_SETTER_MEMBERS.stream()
            .filter(JsonGetterSetterMember::writable)
            .forEach(member -> writeJsonGetterSetterBeanProperty(bean, member));

        JsonNode json = objectMapper.valueToTree(bean);

        for (JsonGetterSetterMember member : JSON_GETTER_SETTER_MEMBERS) {
            if (member.readable()) {
                assertEquals(
                    member.value(),
                    json.get(member.jsonPropertyName()).asString(),
                    member.beanPropertyName()
                );
            } else {
                assertFalse(json.has(member.jsonPropertyName()), member.beanPropertyName());
            }
        }

        JsonGetterSetterBean deserialized = objectMapper.readValue(
            objectMapper.writeValueAsString(writableJsonGetterSetterProperties()),
            JsonGetterSetterBean.class
        );

        for (JsonGetterSetterMember member : JSON_GETTER_SETTER_MEMBERS) {
            if (member.writable()) {
                assertEquals(member.value(), readJsonGetterSetterBeanProperty(deserialized, member), member.beanPropertyName());
            }
        }
    }

    @Test
    void hateoasJsonPropertyMembersAreRecognizedAsReadWriteBeanProperties() {
        BeanIntrospection<JsonError> introspection = BeanIntrospection.getIntrospection(JsonError.class);
        JsonError bean = new JsonError(ISSUE_691_MESSAGE);

        for (HateoasProperty member : HATEOAS_PROPERTIES) {
            BeanProperty<JsonError, Object> property = introspection.getProperty(member.beanPropertyName())
                .orElseThrow();
            assertEquals(
                member.jsonPropertyName(),
                property.stringValue(Introspected.Property.class, "name").orElseThrow(),
                member.beanPropertyName()
            );
            assertEquals(
                List.of(Introspected.Property.Access.READ, Introspected.Property.Access.WRITE),
                List.of(property.enumValues(
                    Introspected.Property.class,
                    "accessKind",
                    Introspected.Property.Access.class
                )),
                member.beanPropertyName()
            );
            assertFalse(property.isReadOnly(), member.beanPropertyName());
            assertFalse(property.isWriteOnly(), member.beanPropertyName());

            BeanReadProperty<JsonError, Object> readProperty = introspection.getReadProperty(member.beanPropertyName())
                .orElseThrow();
            BeanWriteProperty<JsonError, Object> writeProperty = introspection.getWriteProperty(member.beanPropertyName())
                .orElseThrow();
            assertEquals(member.readType(), readProperty.getType(), member.beanPropertyName());
            assertEquals(member.writeType(), writeProperty.getType(), member.beanPropertyName());

            writeProperty.set(bean, member.writeValue());
            assertInstanceOf(member.readType(), readProperty.get(bean), member.beanPropertyName());
            assertInstanceOf(member.readType(), property.get(bean), member.beanPropertyName());
        }

        assertIssue691Resource(bean);
    }

    @Test
    void jacksonDatabindReadsAndWritesIssue691JsonErrorWithoutMicronautSerialization() throws IOException {
        try (ApplicationContext context = ApplicationContext.run()) {
            JsonMapper jsonMapper = context.getBean(JsonMapper.class);

            JsonError deserializationResult = jsonMapper.readValue(ISSUE_691_JSON, JsonError.class);
            assertIssue691JsonError(deserializationResult);

            String serialized = jsonMapper.writeValueAsString(deserializationResult);
            JsonNode json = new ObjectMapper().readTree(serialized);
            assertIssue691Json(json);

            Resource resource = jsonMapper.readValue(ISSUE_691_JSON, Argument.of(Resource.class));
            GenericResource genericResource = assertInstanceOf(GenericResource.class, resource);
            assertEquals(ISSUE_691_MESSAGE, genericResource.getAdditionalProperties().get("message"));
            assertIssue691Resource(genericResource);
        }
    }

    @Test
    void introspectedPropertyValueIsTrackedAsNameAnnotationValue() {
        BeanIntrospection<IntrospectedPropertyBean> introspection =
            BeanIntrospection.getIntrospection(IntrospectedPropertyBean.class);
        IntrospectedPropertyBean bean = new IntrospectedPropertyBean();

        for (IntrospectedPropertyMember member : INTROSPECTED_PROPERTY_MEMBERS) {
            BeanProperty<IntrospectedPropertyBean, String> property =
                introspection.getRequiredProperty(member.beanPropertyName(), String.class);
            assertEquals(
                member.externalPropertyName(),
                property.stringValue(Introspected.Property.class).orElseThrow(),
                member.beanPropertyName()
            );
            assertEquals(
                member.externalPropertyName(),
                property.stringValue(Introspected.Property.class, "name").orElseThrow(),
                member.beanPropertyName()
            );

            property.set(bean, member.value());
            assertEquals(member.value(), property.get(bean), member.beanPropertyName());
        }
    }

    @Test
    void introspectedPropertyCanIgnoreOtherAccessors() {
        BeanIntrospection<IntrospectedPropertyIgnoreAccessorsBean> introspection =
            BeanIntrospection.getIntrospection(IntrospectedPropertyIgnoreAccessorsBean.class);
        BeanProperty<IntrospectedPropertyIgnoreAccessorsBean, String> property =
            introspection.getRequiredProperty("memberAccess", String.class);
        IntrospectedPropertyIgnoreAccessorsBean bean = new IntrospectedPropertyIgnoreAccessorsBean();

        assertEquals(
            "member_access",
            property.stringValue(Introspected.Property.class, "name").orElseThrow()
        );
        assertEquals(
            true,
            property.booleanValue(Introspected.Property.class, "ignoreOtherAccessors").orElseThrow()
        );

        property.set(bean, "member");
        assertEquals("member", bean.memberAccess);
        assertEquals("member", property.get(bean));
    }

    private static Map<String, String> writableJsonProperties() {
        return JSON_PROPERTY_MEMBERS.stream()
            .filter(JsonPropertyMember::writable)
            .collect(Collectors.toMap(
                JsonPropertyMember::jsonPropertyName,
                JsonPropertyMember::value,
                (first, second) -> second,
                LinkedHashMap::new
            ));
    }

    private static Map<String, String> writableJsonGetterSetterProperties() {
        return JSON_GETTER_SETTER_MEMBERS.stream()
            .filter(JsonGetterSetterMember::writable)
            .collect(Collectors.toMap(
                JsonGetterSetterMember::jsonPropertyName,
                JsonGetterSetterMember::value,
                (first, second) -> second,
                LinkedHashMap::new
            ));
    }

    private static Map<String, Boolean> writableJsonBooleanProperties() {
        return JSON_PROPERTY_BOOLEAN_MEMBERS.stream()
            .collect(Collectors.toMap(
                JsonPropertyBooleanMember::jsonPropertyName,
                JsonPropertyBooleanMember::value,
                (first, second) -> second,
                LinkedHashMap::new
            ));
    }

    private static Map<String, String> allJsonAccessorProperties() {
        return JSON_PROPERTY_ACCESSOR_MEMBERS.stream()
            .collect(Collectors.toMap(
                JsonPropertyAccessorMember::jsonPropertyName,
                JsonPropertyAccessorMember::value,
                (first, second) -> second,
                LinkedHashMap::new
            ));
    }

    private static void writeBeanProperty(JsonPropertyBean bean, JsonPropertyMember member) {
        switch (member.beanPropertyName()) {
            case "fieldProperty" -> bean.fieldProperty = member.value();
            case "writeOnlyFieldProperty" -> bean.writeOnlyFieldProperty = member.value();
            case "pairedProperty" -> bean.pairedProperty(member.value());
            case "writeOnlyProperty" -> bean.writeOnlyProperty(member.value());
            case "writeOnlyAccessorProperty" -> bean.setWriteOnlyAccessorProperty(member.value());
            default -> throw new IllegalArgumentException("No writer for " + member.beanPropertyName());
        }
    }

    private static String readBeanProperty(JsonPropertyBean bean, JsonPropertyMember member) {
        return switch (member.beanPropertyName()) {
            case "fieldProperty" -> bean.fieldProperty;
            case "readOnlyFieldProperty" -> bean.readOnlyFieldProperty;
            case "writeOnlyFieldProperty" -> bean.writeOnlyFieldProperty;
            case "pairedProperty" -> bean.pairedProperty();
            case "readOnlyProperty" -> bean.readOnlyProperty();
            case "writeOnlyProperty" -> bean.writeOnlyProperty;
            case "readOnlyAccessorProperty" -> bean.getReadOnlyAccessorProperty();
            case "writeOnlyAccessorProperty" -> bean.getWriteOnlyAccessorProperty();
            default -> throw new IllegalArgumentException("No reader for " + member.beanPropertyName());
        };
    }

    private static void writeAccessorBeanProperty(JsonPropertyAccessorBean bean, JsonPropertyAccessorMember member) {
        switch (member.beanPropertyName()) {
            case "fieldWithAccessors" -> bean.setFieldWithAccessors(member.value());
            case "writeOnlyFieldWithAccessors" -> bean.setWriteOnlyFieldWithAccessors(member.value());
            default -> throw new IllegalArgumentException("No writer for " + member.beanPropertyName());
        }
    }

    private static String readAccessorBeanField(JsonPropertyAccessorBean bean, JsonPropertyAccessorMember member) {
        return switch (member.beanPropertyName()) {
            case "fieldWithAccessors" -> bean.fieldWithAccessors;
            case "readOnlyFieldWithAccessors" -> bean.readOnlyFieldWithAccessors;
            case "writeOnlyFieldWithAccessors" -> bean.writeOnlyFieldWithAccessors;
            default -> throw new IllegalArgumentException("No field for " + member.beanPropertyName());
        };
    }

    private static void writeJsonGetterSetterBeanProperty(JsonGetterSetterBean bean, JsonGetterSetterMember member) {
        switch (member.beanPropertyName()) {
            case "readWriteAccessor" -> bean.setReadWriteAccessor(member.value());
            case "getterWithImplicitSetter" -> bean.setGetterWithImplicitSetter(member.value());
            case "setterWithImplicitGetter" -> bean.setSetterWithImplicitGetter(member.value());
            case "writeOnlyAccessor" -> bean.setWriteOnlyAccessor(member.value());
            case "fluentAccessor" -> bean.fluentAccessor(member.value());
            default -> throw new IllegalArgumentException("No writer for " + member.beanPropertyName());
        }
    }

    private static String readJsonGetterSetterBeanProperty(JsonGetterSetterBean bean, JsonGetterSetterMember member) {
        return switch (member.beanPropertyName()) {
            case "readWriteAccessor" -> bean.getReadWriteAccessor();
            case "getterWithImplicitSetter" -> bean.getGetterWithImplicitSetter();
            case "setterWithImplicitGetter" -> bean.getSetterWithImplicitGetter();
            case "readOnlyAccessor" -> bean.getReadOnlyAccessor();
            case "writeOnlyAccessor" -> bean.writeOnlyAccessor;
            case "fluentAccessor" -> bean.fluentAccessor();
            default -> throw new IllegalArgumentException("No reader for " + member.beanPropertyName());
        };
    }

    private static void writeBooleanBeanProperty(JsonPropertyBooleanBean bean, JsonPropertyBooleanMember member) {
        switch (member.beanPropertyName()) {
            case "active" -> bean.setActive(member.value());
            case "visible" -> bean.visible(member.value());
            default -> throw new IllegalArgumentException("No writer for " + member.beanPropertyName());
        }
    }

    private static boolean readBooleanBeanProperty(JsonPropertyBooleanBean bean, JsonPropertyBooleanMember member) {
        return switch (member.beanPropertyName()) {
            case "active" -> bean.isActive();
            case "visible" -> bean.visible();
            default -> throw new IllegalArgumentException("No reader for " + member.beanPropertyName());
        };
    }

    private static void assertReadWriteJsonProperty(BeanProperty<?, ?> property,
                                                    String beanPropertyName,
                                                    String jsonPropertyName) {
        assertEquals(
            jsonPropertyName,
            property.stringValue(JsonProperty.class).orElseThrow(),
            beanPropertyName
        );
        assertEquals(
            jsonPropertyName,
            property.stringValue(Introspected.Property.class, "name").orElseThrow(),
            beanPropertyName
        );
        assertEquals(
            List.of(Introspected.Property.Access.READ, Introspected.Property.Access.WRITE),
            List.of(property.enumValues(
                Introspected.Property.class,
                "accessKind",
                Introspected.Property.Access.class
            )),
            beanPropertyName
        );
        assertFalse(property.isReadOnly(), beanPropertyName);
        assertFalse(property.isWriteOnly(), beanPropertyName);
    }

    private static void assertIssue691JsonError(JsonError jsonError) {
        assertNotNull(jsonError);
        assertEquals(ISSUE_691_MESSAGE, jsonError.getMessage());
        assertIssue691Resource(jsonError);
    }

    private static void assertIssue691Resource(Resource resource) {
        assertNotNull(resource);
        Link link = resource.getLinks().getFirst(Link.SELF).orElseThrow();
        assertEquals(ISSUE_691_LINK, link.getHref());

        Resource embedded = resource.getEmbedded().getFirst("errors").orElseThrow();
        assertEquals(ISSUE_691_EMBEDDED_MESSAGE, resourceMessage(embedded));
    }

    private static String resourceMessage(Resource resource) {
        if (resource instanceof JsonError jsonError) {
            return jsonError.getMessage();
        }
        GenericResource genericResource = assertInstanceOf(GenericResource.class, resource);
        return (String) genericResource.getAdditionalProperties().get("message");
    }

    private static void assertIssue691Json(JsonNode json) {
        assertEquals(ISSUE_691_MESSAGE, json.get("message").asString());
        assertFalse(json.has("links"));
        assertFalse(json.has("embedded"));
        assertEquals(
            ISSUE_691_LINK,
            firstOrSingle(json.get(Resource.LINKS).get(Link.SELF.toString())).get(Link.HREF.toString()).asString()
        );
        assertEquals(
            ISSUE_691_EMBEDDED_MESSAGE,
            firstOrSingle(json.get(Resource.EMBEDDED).get("errors")).get("message").asString()
        );
    }

    private static JsonNode firstOrSingle(JsonNode json) {
        assertNotNull(json);
        JsonNode value = json.isArray() ? json.get(0) : json;
        assertNotNull(value);
        return value;
    }

    private record JsonPropertyMember(
        String beanPropertyName,
        String jsonPropertyName,
        String value,
        boolean readable,
        boolean writable
    ) {
        List<Introspected.Property.Access> accessKinds() {
            if (readable && writable) {
                return List.of(Introspected.Property.Access.READ, Introspected.Property.Access.WRITE);
            }
            if (readable) {
                return List.of(Introspected.Property.Access.READ);
            }
            return List.of(Introspected.Property.Access.WRITE);
        }
    }

    private record JsonGetterSetterMember(
        String beanPropertyName,
        String jsonPropertyName,
        String value,
        boolean readable,
        boolean writable
    ) {
    }

    private record JsonPropertyAccessorMember(
        String beanPropertyName,
        String jsonPropertyName,
        String value,
        boolean readable,
        boolean writable,
        String storedValue,
        String readValue
    ) {
    }

    private record JsonPropertyBooleanMember(
        String beanPropertyName,
        String jsonPropertyName,
        boolean value
    ) {
    }

    private record JsonPropertyBooleanSetterWithoutParameter(
        String beanPropertyName,
        String jsonPropertyName
    ) {
    }

    private record HateoasProperty(
        String beanPropertyName,
        String jsonPropertyName,
        Object writeValue,
        Class<?> readType,
        Class<?> writeType
    ) {
    }

    private record IntrospectedPropertyMember(
        String beanPropertyName,
        String externalPropertyName,
        String value
    ) {
    }

    @Introspected
    static final class JsonPropertyBean {
        @JsonProperty("field_property")
        String fieldProperty;

        @JsonProperty(value = "read_only_field_property", access = JsonProperty.Access.READ_ONLY)
        String readOnlyFieldProperty = "read-only-field";

        @JsonProperty(value = "write_only_field_property", access = JsonProperty.Access.WRITE_ONLY)
        String writeOnlyFieldProperty;

        private String pairedProperty;
        private String writeOnlyProperty;
        private String readOnlyAccessorProperty = "read-only-accessor";
        private String writeOnlyAccessorProperty;

        @JsonProperty(value = "paired_property", access = JsonProperty.Access.READ_WRITE)
        public String pairedProperty() {
            return pairedProperty;
        }

        @JsonProperty(value = "paired_property", access = JsonProperty.Access.READ_WRITE)
        public void pairedProperty(String pairedProperty) {
            this.pairedProperty = pairedProperty;
        }

        @JsonProperty(value = "read_only_property", access = JsonProperty.Access.READ_ONLY)
        public String readOnlyProperty() {
            return "read-only";
        }

        @JsonProperty(value = "write_only_property", access = JsonProperty.Access.WRITE_ONLY)
        public void writeOnlyProperty(String writeOnlyProperty) {
            this.writeOnlyProperty = writeOnlyProperty;
        }

        @JsonProperty(value = "read_only_accessor_property", access = JsonProperty.Access.READ_ONLY)
        public String getReadOnlyAccessorProperty() {
            return readOnlyAccessorProperty;
        }

        public void setReadOnlyAccessorProperty(String readOnlyAccessorProperty) {
            this.readOnlyAccessorProperty = readOnlyAccessorProperty;
        }

        public String getWriteOnlyAccessorProperty() {
            return writeOnlyAccessorProperty;
        }

        @JsonProperty(value = "write_only_accessor_property", access = JsonProperty.Access.WRITE_ONLY)
        public void setWriteOnlyAccessorProperty(String writeOnlyAccessorProperty) {
            this.writeOnlyAccessorProperty = writeOnlyAccessorProperty;
        }
    }

    @Introspected
    static final class JsonPropertyAccessorBean {
        @JsonProperty("field_with_accessors")
        String fieldWithAccessors;

        @JsonProperty(value = "read_only_field_with_accessors", access = JsonProperty.Access.READ_ONLY)
        String readOnlyFieldWithAccessors = "read-only-field-with-accessors";

        @JsonProperty(value = "write_only_field_with_accessors", access = JsonProperty.Access.WRITE_ONLY)
        String writeOnlyFieldWithAccessors;

        public String getFieldWithAccessors() {
            return fieldWithAccessors + "-getter";
        }

        public void setFieldWithAccessors(String fieldWithAccessors) {
            this.fieldWithAccessors = fieldWithAccessors + "-setter";
        }

        public String getReadOnlyFieldWithAccessors() {
            return readOnlyFieldWithAccessors + "-getter";
        }

        public void setReadOnlyFieldWithAccessors(String readOnlyFieldWithAccessors) {
            this.readOnlyFieldWithAccessors = readOnlyFieldWithAccessors + "-setter";
        }

        public String getWriteOnlyFieldWithAccessors() {
            return writeOnlyFieldWithAccessors + "-getter";
        }

        public void setWriteOnlyFieldWithAccessors(String writeOnlyFieldWithAccessors) {
            this.writeOnlyFieldWithAccessors = writeOnlyFieldWithAccessors + "-setter";
        }
    }

    @Introspected
    static final class JsonGetterSetterBean {
        private String readWriteAccessor;
        private String getterWithImplicitSetter;
        private String setterWithImplicitGetter;
        private String readOnlyAccessor = "read-only";
        private String writeOnlyAccessor;
        private String fluentAccessor;

        @JsonGetter("read_write_accessor")
        public String getReadWriteAccessor() {
            return readWriteAccessor;
        }

        @JsonSetter("read_write_accessor")
        public void setReadWriteAccessor(String readWriteAccessor) {
            this.readWriteAccessor = readWriteAccessor;
        }

        @JsonGetter("getter_with_implicit_setter")
        public String getGetterWithImplicitSetter() {
            return getterWithImplicitSetter;
        }

        public void setGetterWithImplicitSetter(String getterWithImplicitSetter) {
            this.getterWithImplicitSetter = getterWithImplicitSetter;
        }

        public String getSetterWithImplicitGetter() {
            return setterWithImplicitGetter;
        }

        @JsonSetter("setter_with_implicit_getter")
        public void setSetterWithImplicitGetter(String setterWithImplicitGetter) {
            this.setterWithImplicitGetter = setterWithImplicitGetter;
        }

        @JsonGetter("read_only_accessor")
        public String getReadOnlyAccessor() {
            return readOnlyAccessor;
        }

        @JsonSetter("write_only_accessor")
        public void setWriteOnlyAccessor(String writeOnlyAccessor) {
            this.writeOnlyAccessor = writeOnlyAccessor;
        }

        @JsonGetter("fluent_accessor")
        public String fluentAccessor() {
            return fluentAccessor;
        }

        @JsonSetter("fluent_accessor")
        public void fluentAccessor(String fluentAccessor) {
            this.fluentAccessor = fluentAccessor;
        }
    }

    @Introspected
    static final class JsonPropertyBooleanBean {
        private boolean active;
        private boolean visible;
        private boolean debug;
        private boolean methodFlag;

        @JsonProperty("active_flag")
        public boolean isActive() {
            return active;
        }

        @JsonProperty("active_flag")
        public void setActive(boolean active) {
            this.active = active;
        }

        @JsonProperty("visible_flag")
        public boolean visible() {
            return visible;
        }

        @JsonProperty("visible_flag")
        public void visible(boolean visible) {
            this.visible = visible;
        }

        @JsonProperty("debug_flag")
        public boolean isDebug() {
            return debug;
        }

        @JsonProperty("debug_flag")
        public void setDebug() {
            this.debug = true;
        }

        @JsonProperty("method_flag")
        public boolean isMethodFlag() {
            return methodFlag;
        }

        @JsonProperty("method_flag")
        public void methodFlag() {
            this.methodFlag = true;
        }
    }

    @Introspected
    static final class IntrospectedPropertyBean {
        private String valueAccessor;
        private String namedAccessor;

        @Introspected.Property("value_accessor")
        public String valueAccessor() {
            return valueAccessor;
        }

        @Introspected.Property("value_accessor")
        public void valueAccessor(String valueAccessor) {
            this.valueAccessor = valueAccessor;
        }

        @Introspected.Property(name = "named_accessor")
        public String namedAccessor() {
            return namedAccessor;
        }

        @Introspected.Property(name = "named_accessor")
        public void namedAccessor(String namedAccessor) {
            this.namedAccessor = namedAccessor;
        }
    }

    @Introspected
    static final class IntrospectedPropertyIgnoreAccessorsBean {
        @Introspected.Property(name = "member_access", ignoreOtherAccessors = true)
        String memberAccess;

        public String getMemberAccess() {
            return memberAccess + "-getter";
        }

        public void setMemberAccess(String memberAccess) {
            this.memberAccess = memberAccess + "-setter";
        }
    }
}
