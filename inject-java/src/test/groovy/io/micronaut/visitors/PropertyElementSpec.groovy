/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Get
import io.micronaut.inject.ast.ClassElement
import jakarta.annotation.Nullable
import jakarta.validation.constraints.NotBlank

class PropertyElementSpec extends AbstractTypeElementSpec {

    void 'test record component annotation'() {
        given:
            ClassElement classElement = buildClassElement('''
package test;

record Book(@io.micronaut.visitors.MyRecordComponentAnn(name = "test123") String title, int pages) {}
''')
            def beanProperties = classElement.getBeanProperties()
            def titleProp = beanProperties.find { it.name == 'title' }
        expect:
            titleProp.hasAnnotation(MyRecordComponentAnn)
            titleProp.stringValue(MyRecordComponentAnn, "name").get() == "test123"
    }

    void 'test field annotation and records'() {
        given:
            ClassElement classElement = buildClassElement('''
package test;

record Book(@io.micronaut.visitors.MyFieldAnn(name = "test123") String title, int pages) {}
''')
            def beanProperties = classElement.getBeanProperties()
            def titleProp = beanProperties.find { it.name == 'title' }
        expect:
            titleProp.hasAnnotation(MyFieldAnn)
            titleProp.stringValue(MyFieldAnn, "name").get() == "test123"
    }

    void 'test bean properties work for records'() {
        given:
        ClassElement classElement = buildClassElement('''
package test;

record Book( @jakarta.validation.constraints.NotBlank String title, int pages) {}
''')
        def beanProperties = classElement.getBeanProperties()
        def titleProp = beanProperties.find { it.name == 'title' }
        expect:
        classElement.isRecord()
        beanProperties.size() == 2
        titleProp != null
        titleProp.type.name == String.name
        titleProp.hasAnnotation(NotBlank)
        beanProperties.every { it.readOnly }
    }

    void "test simple bean properties"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/test")
public class TestController {

    private int age;
    @jakarta.annotation.Nullable
    private String name;
    @jakarta.annotation.Nullable
    private String description;

    /**
     * The age
     */
    @Get("/getMethod")
    public int getAge() {
        return age;
    }

    /**
     * The age
     */
    @Get("/getMethod/{age}")
    public int getAge( @jakarta.validation.constraints.NotBlank int age) {
        return age;
    }

    public String getName() {
        return name;
    }

    @jakarta.validation.constraints.NotBlank
    public void setName(@jakarta.validation.constraints.NotBlank String n) {
        name = n;
    }

    /**
     * The Description
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(@jakarta.validation.constraints.NotBlank  String description) {
        this.description = description;
    }
}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties.size() == 3
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties.size() == 3
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].name == 'age'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].isAnnotationPresent(Get)
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.name == 'int'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].isReadOnly()
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].name == 'name'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].isAnnotationPresent(Nullable)
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].type.name == 'java.lang.String'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[2].name == 'description'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[2].isAnnotationPresent(Nullable)
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[2].type.name == 'java.lang.String'
        AllElementsVisitor
        !AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].isReadOnly()
    }

    void "test simple bean properties with generics"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

@Controller("/test")
public class TestController<T extends CharSequence> {

    private int age;
    private T name;

    public int getAge() {
        return age;
    }

    public T getName() {
        return name;
    }

    public void setName(T n) {
        name = n;
    }
}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties.size() == 2
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].name == 'age'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.name == 'int'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].isReadOnly()
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].name == 'name'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].type.name == 'java.lang.CharSequence'
        !AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].isReadOnly()
    }


    void "test simple bean properties with generics on property"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

@Controller("/test")
public class TestController {

    private Response<Integer> age;

    public Response<Integer> getAge() {
        return age;
    }

    @Put("/")
    public Response<Integer> update() {
        return null;
    }
}

class Response<T> {
    T r;
    public T getResult() { return r; }
}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].name == 'age'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.name == 'test.Response'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].isReadOnly()
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.typeArguments.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.typeArguments.values().first().name == 'java.lang.Integer'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 2
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].name == 'update'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.name == 'test.Response'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.typeArguments.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.typeArguments.values().first().name == 'java.lang.Integer'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.beanProperties.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.beanProperties[0].type.name == 'java.lang.Integer'
    }

    void "test get annotations from type after bean properties "() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Controller
class TestController {

    @Post("/path")
    public void processSync(@Body MyDto dto) {
    }
}

class MyDto {

    private Parameters parameters;

    public Parameters getParameters() {
        return parameters;
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }
}

@Introspected
class Parameters {

    private Integer stampWidth;
    private Integer stampHeight;
    private int pageNumber;

    public Integer getStampWidth() {
        return stampWidth;
    }

    public void setStampWidth(Integer stampWidth) {
        this.stampWidth = stampWidth;
    }

    public Integer getStampHeight() {
        return stampHeight;
    }

    public void setStampHeight(Integer stampHeight) {
        this.stampHeight = stampHeight;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }
}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1

        def method = AllElementsVisitor.VISITED_METHOD_ELEMENTS[0]
        def parameter = method.parameters[0]

        def beanProperty = parameter.type.beanProperties.get(0)
        beanProperty.type.annotationNames.sort() == [
                'io.micronaut.core.annotation.Introspected'
        ]
        beanProperty.field.get().type.annotationNames.sort() == [
                'io.micronaut.core.annotation.Introspected'
        ]
        beanProperty.readMethod.get().returnType.annotationNames.sort() == [
                'io.micronaut.core.annotation.Introspected'
        ]
        beanProperty.writeMethod.get().parameters[0].type.annotationNames.sort() == [
                'io.micronaut.core.annotation.Introspected'
        ]
    }

    void "test get annotations from type after bean properties for field access"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Controller
class TestController {

    @Post("/path")
    public void processSync(@Body MyDto dto) {
    }
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
class MyDto {

    public Parameters parameters;
}

@Introspected
class Parameters {

    private Integer stampWidth;
    private Integer stampHeight;
    private int pageNumber;

    public Integer getStampWidth() {
        return stampWidth;
    }

    public void setStampWidth(Integer stampWidth) {
        this.stampWidth = stampWidth;
    }

    public Integer getStampHeight() {
        return stampHeight;
    }

    public void setStampHeight(Integer stampHeight) {
        this.stampHeight = stampHeight;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }
}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1

        def method = AllElementsVisitor.VISITED_METHOD_ELEMENTS[0]
        def parameter = method.parameters[0]

        def beanProperty = parameter.type.beanProperties.get(0)
        beanProperty.type.annotationNames.sort() == [
                'io.micronaut.core.annotation.Introspected'
        ]
        beanProperty.field.get().type.annotationNames.sort() == [
                'io.micronaut.core.annotation.Introspected'
        ]
        beanProperty.field.get().genericType.annotationNames.sort() == [
                'io.micronaut.core.annotation.Introspected'
        ]
    }

    void "test conflicting introspected property access kinds fail compilation"() {
        when:
        buildBeanIntrospection('test.ConflictingPropertyAccess', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class ConflictingPropertyAccess {
    private String name;

    @Introspected.Property(accessKind = Introspected.Property.Access.READ)
    public String getName() {
        return name;
    }

    @Introspected.Property(accessKind = Introspected.Property.Access.WRITE)
    public void setName(String name) {
        this.name = name;
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Conflicting @Introspected.Property accessKind declarations for property [name]')
    }

    void "test introspected property value and name must match"() {
        when:
        buildBeanIntrospection('test.ConflictingPropertyNames', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class ConflictingPropertyNames {
    private String name;

    @Introspected.Property(value = "external_name", name = "other_name")
    public String getName() {
        return name;
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('The @Introspected.Property value and name members must match when both are declared')
    }

    void "test inaccessible introspected property field fails compilation"() {
        when:
        buildBeanIntrospection('test.InaccessiblePropertyField', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class InaccessiblePropertyField {
    @Introspected.Property(accessKind = Introspected.Property.Access.READ)
    private String name;
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(
                'Element annotated with @Introspected.Property cannot be used as an introspected property: the field is not accessible'
        )
    }

    void "test inaccessible introspected property method fails compilation"() {
        when:
        buildBeanIntrospection('test.InaccessiblePropertyMethod', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class InaccessiblePropertyMethod {
    @Introspected.Property(accessKind = Introspected.Property.Access.READ)
    private String name() {
        return "test";
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(
                'Element annotated with @Introspected.Property cannot be used as an introspected property: the method is not accessible'
        )
    }

    void "test introspected property method must provide declared access"() {
        when:
        buildBeanIntrospection('test.InvalidPropertyMethodAccess', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class InvalidPropertyMethodAccess {
    @Introspected.Property(accessKind = Introspected.Property.Access.WRITE)
    public String name() {
        return "test";
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(
                'Element annotated with @Introspected.Property cannot be used as an introspected property: ' +
                        'write access requires a one-argument method or a zero-argument void method'
        )
    }

    void "test introspected property field must provide declared access"() {
        when:
        buildBeanIntrospection('test.InvalidPropertyFieldAccess', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class InvalidPropertyFieldAccess {
    @Introspected.Property(accessKind = Introspected.Property.Access.WRITE)
    public final String name = "test";
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains(
                'Element annotated with @Introspected.Property cannot be used as an introspected property: ' +
                        'write access requires a non-final field'
        )
    }

    void "test introspected property method can ignore other accessors"() {
        given:
        ClassElement classElement = buildClassElement('''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class MethodIgnoresOtherAccessors {
    private String name;

    public CharSequence getName() {
        return name;
    }

    @Introspected.Property(ignoreOtherAccessors = true)
    public String name() {
        return name;
    }
}
''')
        def beanProperty = classElement.beanProperties.find { it.name == 'name' }

        expect:
        beanProperty.type.name == String.name
        beanProperty.readMethod.get().name == 'name'
    }

    void "test json auto detect default visibility resolves to jackson defaults"() {
        given:
        ClassElement classElement = buildClassElement('''
package test;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.micronaut.core.annotation.Introspected;

@Introspected
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.DEFAULT,
    getterVisibility = JsonAutoDetect.Visibility.DEFAULT,
    isGetterVisibility = JsonAutoDetect.Visibility.DEFAULT,
    setterVisibility = JsonAutoDetect.Visibility.DEFAULT
)
class DefaultJsonAutoDetectBean {
    private String privateField;
    public String publicField;
    private String name;
    private boolean active;
    private String writeOnly;
    private String protectedSetter;

    public String getName() {
        return name;
    }

    protected String getProtectedGetter() {
        return "protected";
    }

    public boolean isActive() {
        return active;
    }

    public void setWriteOnly(String writeOnly) {
        this.writeOnly = writeOnly;
    }

    protected void setProtectedSetter(String protectedSetter) {
        this.protectedSetter = protectedSetter;
    }
}
''')
        def properties = classElement.beanProperties
        def propertyNames = properties*.name as Set

        expect:
        propertyNames == ['publicField', 'name', 'active', 'writeOnly', 'protectedSetter'] as Set
        !properties.find { it.name == 'publicField' }.isReadOnly()
        !properties.find { it.name == 'publicField' }.isWriteOnly()
        properties.find { it.name == 'name' }.isReadOnly()
        properties.find { it.name == 'active' }.isReadOnly()
        properties.find { it.name == 'writeOnly' }.isWriteOnly()
        properties.find { it.name == 'protectedSetter' }.isWriteOnly()
    }

    void "test json auto detect non private and protected public visibility"() {
        given:
        ClassElement classElement = buildClassElement('''
package test;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.micronaut.core.annotation.Introspected;

@Introspected
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NON_PRIVATE,
    getterVisibility = JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC,
    isGetterVisibility = JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC,
    setterVisibility = JsonAutoDetect.Visibility.NONE
)
class RestrictedJsonAutoDetectBean {
    private String privateField;
    String packageField;
    protected String protectedField;
    public String publicField;
    private String protectedGetter;
    private boolean active;
    private String writeOnly;

    protected String getProtectedGetter() {
        return protectedGetter;
    }

    String getPackageGetter() {
        return "package";
    }

    public boolean isActive() {
        return active;
    }

    public void setWriteOnly(String writeOnly) {
        this.writeOnly = writeOnly;
    }
}
''')
        def properties = classElement.beanProperties
        def propertyNames = properties*.name as Set

        expect:
        propertyNames == ['packageField', 'protectedField', 'publicField', 'protectedGetter', 'active'] as Set
        !properties.find { it.name == 'packageField' }.isReadOnly()
        !properties.find { it.name == 'packageField' }.isWriteOnly()
        !properties.find { it.name == 'protectedField' }.isReadOnly()
        !properties.find { it.name == 'protectedField' }.isWriteOnly()
        !properties.find { it.name == 'publicField' }.isReadOnly()
        !properties.find { it.name == 'publicField' }.isWriteOnly()
        properties.find { it.name == 'protectedGetter' }.isReadOnly()
        properties.find { it.name == 'active' }.isReadOnly()
    }

    void "test jackson property annotations define explicit property access"() {
        given:
        def introspection = buildBeanIntrospection('test.JacksonPropertyAccessBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.core.annotation.Introspected;

@Introspected
class JacksonPropertyAccessBean {
    private String renamed;
    private String readOnly;
    private String writeOnly;
    private String setterOnly;

    @JsonProperty("renamed_value")
    public String getRenamed() {
        return renamed;
    }

    @JsonProperty("renamed_value")
    public void setRenamed(String renamed) {
        this.renamed = renamed;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getReadOnly() {
        return readOnly;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public void setWriteOnly(String writeOnly) {
        this.writeOnly = writeOnly;
    }

    @JsonSetter("setter_only")
    public void applySetterOnly(String setterOnly) {
        this.setterOnly = setterOnly;
    }
}
''')

        def properties = introspection.beanProperties
        def renamed = properties.find { it.name == 'renamed' }
        def readOnly = properties.find { it.name == 'readOnly' }
        def writeOnly = properties.find { it.name == 'writeOnly' }
        def setterOnly = properties.find { it.name == 'applySetterOnly' }

        expect:
        properties*.name as Set == ['renamed', 'readOnly', 'writeOnly', 'applySetterOnly'] as Set
        !renamed.isReadOnly()
        !renamed.isWriteOnly()
        readOnly.isReadOnly()
        writeOnly.isWriteOnly()
        setterOnly.isWriteOnly()
    }

    void "test jackson property on generated dollar prefixed private field uses public accessors"() {
        given:
        def introspection = buildBeanIntrospection('test.JacksonDollarPrefixedFieldBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
class JacksonDollarPrefixedFieldBean {
    public static final String JSON_PROPERTY_$_REF = "$ref";

    @JsonProperty(JSON_PROPERTY_$_REF)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private String $ref;

    public String get$Ref() {
        return $ref;
    }

    public void set$Ref(String $ref) {
        this.$ref = $ref;
    }
}
''')
        def property = introspection.getRequiredProperty('$Ref', String)

        expect:
        property.stringValue(Introspected.Property, "name").orElseThrow() == '$ref'
    }

    void "test jackson property on generated underscore prefixed private field uses public accessors"() {
        given:
        def introspection = buildBeanIntrospection('test.JacksonUnderscorePrefixedFieldBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
class JacksonUnderscorePrefixedFieldBean {
    public static final String JSON_PROPERTY_DEFAULT = "default";

    @JsonProperty(JSON_PROPERTY_DEFAULT)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private String _default;

    public String get_default() {
        return _default;
    }

    public void set_default(String _default) {
        this._default = _default;
    }
}
''')
        def property = introspection.getRequiredProperty('_default', String)

        expect:
        property.stringValue(Introspected.Property, "name").orElseThrow() == 'default'
    }

    void "test jackson write only final dollar prefixed field resolves as constructor property"() {
        given:
        def introspection = buildBeanIntrospection('test.JacksonWriteOnlyDollarConstructorGetterBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
class JacksonWriteOnlyDollarConstructorGetterBean {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private final String $ref;

    @JsonCreator
    JacksonWriteOnlyDollarConstructorGetterBean(@JsonProperty("$ref") String $ref) {
        this.$ref = $ref;
    }

    public String get$Ref() {
        return $ref;
    }
}
''')
        def property = introspection.getRequiredProperty('$Ref', String)
        def writeProperty = introspection.getRequiredWriteProperty('$Ref', String)
        def bean = introspection.instantiate('value')
        def updated = writeProperty.withValue(bean, 'updated')

        expect:
        property.isWriteOnly()
        !property.isReadOnly()
        introspection.getReadProperty('$Ref').isEmpty()
        introspection.getWriteProperty('$Ref').isPresent()
        updated.get$Ref() == 'updated'
    }

    void "test jackson write only final underscore prefixed field resolves as constructor property"() {
        given:
        def introspection = buildBeanIntrospection('test.JacksonWriteOnlyUnderscoreConstructorGetterBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
class JacksonWriteOnlyUnderscoreConstructorGetterBean {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private final String _default;

    @JsonCreator
    JacksonWriteOnlyUnderscoreConstructorGetterBean(@JsonProperty("default") String _default) {
        this._default = _default;
    }

    public String get_default() {
        return _default;
    }
}
''')
        def property = introspection.getRequiredProperty('_default', String)
        def writeProperty = introspection.getRequiredWriteProperty('_default', String)
        def bean = introspection.instantiate('value')
        def updated = writeProperty.withValue(bean, 'updated')

        expect:
        property.isWriteOnly()
        !property.isReadOnly()
        introspection.getReadProperty('_default').isEmpty()
        introspection.getWriteProperty('_default').isPresent()
        updated.get_default() == 'updated'
    }

    void "test jackson write only dollar prefixed record component resolves as constructor property"() {
        given:
        def introspection = buildBeanIntrospection('test.JacksonWriteOnlyDollarRecord', '''
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
record JacksonWriteOnlyDollarRecord(
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String $ref
) {
}
''')
        def property = introspection.getRequiredProperty('$ref', String)
        def writeProperty = introspection.getRequiredWriteProperty('$ref', String)
        def bean = introspection.instantiate('value')
        def updated = writeProperty.withValue(bean, 'updated')

        expect:
        property.isWriteOnly()
        !property.isReadOnly()
        introspection.getReadProperty('$ref').isEmpty()
        introspection.getWriteProperty('$ref').isPresent()
        updated.$ref() == 'updated'
    }

    void "test jackson write only underscore prefixed record component resolves as constructor property"() {
        given:
        def introspection = buildBeanIntrospection('test.JacksonWriteOnlyUnderscoreRecord', '''
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
record JacksonWriteOnlyUnderscoreRecord(
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String _default
) {
}
''')
        def property = introspection.getRequiredProperty('_default', String)
        def writeProperty = introspection.getRequiredWriteProperty('_default', String)
        def bean = introspection.instantiate('value')
        def updated = writeProperty.withValue(bean, 'updated')

        expect:
        property.isWriteOnly()
        !property.isReadOnly()
        introspection.getReadProperty('_default').isEmpty()
        introspection.getWriteProperty('_default').isPresent()
        updated._default() == 'updated'
    }

    void "test jackson write only record component resolves as write only constructor property"() {
        given:
        def introspection = buildBeanIntrospection('test.JacksonWriteOnlyRecord', '''
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
record JacksonWriteOnlyRecord(
    @JsonProperty
    String value,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String ignored
) {
}
''')
        def ignored = introspection.getRequiredProperty('ignored', String)
        def writeProperty = introspection.getRequiredWriteProperty('ignored', String)
        def bean = introspection.instantiate('value', 'ignored')
        def updated = writeProperty.withValue(bean, 'updated')

        expect:
        ignored.isWriteOnly()
        !ignored.isReadOnly()
        introspection.getReadProperty('ignored').isEmpty()
        introspection.getWriteProperty('ignored').isPresent()
        updated.ignored() == 'updated'
    }

    void "test jackson write only constructor getter bean resolves as write only constructor property"() {
        given:
        def introspection = buildBeanIntrospection('test.JacksonWriteOnlyConstructorGetterBean', '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
class JacksonWriteOnlyConstructorGetterBean {
    private final String value;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private final String ignored;

    @JsonCreator
    JacksonWriteOnlyConstructorGetterBean(@JsonProperty("value") String value, @JsonProperty("ignored") String ignored) {
        this.value = value;
        this.ignored = ignored;
    }

    public String getValue() {
        return value;
    }

    public String getIgnored() {
        return ignored;
    }
}
''')
        def ignored = introspection.getRequiredProperty('ignored', String)
        def writeProperty = introspection.getRequiredWriteProperty('ignored', String)
        def bean = introspection.instantiate('value', 'ignored')
        def updated = writeProperty.withValue(bean, 'updated')

        expect:
        ignored.isWriteOnly()
        !ignored.isReadOnly()
        introspection.getReadProperty('ignored').isEmpty()
        introspection.getWriteProperty('ignored').isPresent()
        updated.getIgnored() == 'updated'
    }
}
