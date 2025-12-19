package io.micronaut.python.annotation.processing.test.annotate

import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Requirements
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.TypeHint
import io.micronaut.core.annotation.Nullable
import io.micronaut.inject.BeanDefinition
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.python.compiler.PrimitiveTypesAnnotation
import spock.lang.PendingFeature

class AnnotationMetadataWriterSpec extends AbstractPythonTypeElementSpec {

    void "test read enum constants"() {
        given:
        BeanDefinition definition = buildBeanDefinition('python', 'Test', '''
from micronaut.context.annotation import Requires
from jakarta.inject import Singleton

@Requires(sdk="JAVA", version="1.8")
@Singleton
class Test:
    pass
''')

        when:
        AnnotationMetadata metadata = definition.getAnnotationMetadata()

        then:
        metadata != null
        metadata.enumValue(Requires, "sdk", Requires.Sdk).get() == Requires.Sdk.JAVA
        metadata.getValue(Requires, "sdk", Requires.Sdk).get() == Requires.Sdk.JAVA
        metadata.getValue(Requires, "version").get() == "1.8"
    }

    void "test read external constants"() {
        given:
        BeanDefinition definition = buildBeanDefinition('python', 'Test', '''
from micronaut.context.annotation import Requires
from micronaut.core.annotation import AnnotationMetadata
from jakarta.inject import Singleton

@Requires(property=AnnotationMetadata.VALUE_MEMBER)
@Singleton
class Test:
    pass
''')

        when:
        AnnotationMetadata metadata = definition.getAnnotationMetadata()

        then:
        metadata != null
        metadata.getValue(Requires, "property").isPresent()
        metadata.getValue(Requires, "property").get() == 'value'
    }

    void "test repeatable annotations are combined"() {
        given:
        BeanDefinition definition = buildBeanDefinition('python', 'Test', '''
from micronaut.context.annotation import Property, Executable
from jakarta.inject import Singleton

@Property(name="prop1", value="value1")
@Property(name="prop2", value="value2")
@Property(name="prop3", value="value3")
@Singleton
class Test:

    @Property(name="prop2", value="value2")
    @Property(name="prop3", value="value33")
    @Property(name="prop4", value="value4")
    @Executable
    def someMethod(self):
        pass
''')

        when:
        AnnotationMetadata metadata = definition.getRequiredMethod("someMethod").getAnnotationMetadata()

        then:
        List<AnnotationValue<Property>> properties = metadata.getAnnotationValuesByType(Property)

        and:
        properties.size() == 5
        properties[0].get("name", String).get() == "prop2"
        properties[1].get("name", String).get() == "prop3"
        properties[1].getValue(String).get() == "value33"
        properties[2].get("name", String).get() == "prop4"
        properties[3].get("name", String).get() == "prop1"
        properties[4].get("name", String).get() == "prop3"
        properties[4].getValue(String).get() == "value3"
    }

    void "test repeatable annotations are combined, lookup by name"() {
        given:
        BeanDefinition definition = buildBeanDefinition('python', 'Test', '''
from micronaut.context.annotation import Property, Executable
from jakarta.inject import Singleton

@Property(name="prop1", value="value1")
@Property(name="prop2", value="value2")
@Property(name="prop3", value="value3")
@Singleton
class Test:

    @Property(name="prop2", value="value2")
    @Property(name="prop3", value="value33")
    @Property(name="prop4", value="value4")
    @Executable
    def someMethod(self):
        pass
''')

        when:
        AnnotationMetadata metadata = definition.getRequiredMethod("someMethod").getAnnotationMetadata()

        then:
        List<AnnotationValue<?>> properties = metadata.getAnnotationValuesByName(Property.name)

        and:
        properties.size() == 5
        properties[0].get("name", String).get() == "prop2"
        properties[1].get("name", String).get() == "prop3"
        properties[1].getValue(String).get() == "value33"
        properties[2].get("name", String).get() == "prop4"
        properties[3].get("name", String).get() == "prop1"
        properties[4].get("name", String).get() == "prop3"
        properties[4].getValue(String).get() == "value3"
    }

    void "test declared repeatable annotations are combined, lookup by name"() {
        given:
        BeanDefinition definition = buildBeanDefinition('python', 'Test', '''
from micronaut.context.annotation import Property, Executable
from jakarta.inject import Singleton

@Property(name="prop1", value="value1")
@Property(name="prop2", value="value2")
@Property(name="prop3", value="value3")
@Singleton
class Test:

    @Property(name="prop2", value="value2")
    @Property(name="prop3", value="value33")
    @Property(name="prop4", value="value4")
    @Executable
    def someMethod(self):
        pass
''')

        when:
        AnnotationMetadata metadata = definition.getRequiredMethod("someMethod").getAnnotationMetadata()

        then:
        List<AnnotationValue<?>> properties = metadata.getDeclaredAnnotationValuesByName(Property.name)

        and:
        properties.size() == 3
        properties[0].get("name", String).get() == "prop2"
        properties[1].get("name", String).get() == "prop3"
        properties[1].getValue(String).get() == "value33"
        properties[2].get("name", String).get() == "prop4"
    }

    void "test write first level stereotype data (@Primary)"() {
        given:
        BeanDefinition definition = buildBeanDefinition('python', 'Test', '''
from micronaut.context.annotation import Primary

@Primary
class Test:
    pass
''')

        when:
        AnnotationMetadata metadata = definition.getAnnotationMetadata()

        then:
        metadata != null
        metadata.hasDeclaredAnnotation(Primary)
        metadata.hasAnnotation(Primary)
        metadata.hasStereotype(AnnotationUtil.QUALIFIER)
        !metadata.hasStereotype(AnnotationUtil.SINGLETON) // stereotype, not declared directly here
    }

    void "test build repeatable @Requires are wrapped into @Requirements with values"() {
        given:
        BeanDefinition definition = buildBeanDefinition('python', 'Test', '''
from micronaut.context.annotation import Requires
from jakarta.inject import Singleton

@Requires(property="blah")
@Requires(property="foo")
@Singleton
class Test:
    pass
''')

        when:
        AnnotationMetadata metadata = definition.getAnnotationMetadata()

        then:
        metadata != null
        metadata.hasDeclaredAnnotation(Requirements)
        def arr = metadata.getValue(Requirements).get()
        arr.length == 2
        arr[0] instanceof io.micronaut.core.annotation.AnnotationValue
        arr[1] instanceof io.micronaut.core.annotation.AnnotationValue
        arr[0].values.get('property') == 'blah'
        arr[1].values.get('property') == 'foo'
    }

    void "test annotation metadata string value array types with @TypeHint"() {
        given:
        BeanDefinition definition = buildBeanDefinition('python', 'Test', """
from micronaut.core.annotation import TypeHint
from jakarta.inject import Singleton

@TypeHint(value=["[Ljava.util.UUID;", "java.util.UUID"])
@Singleton
class Test:
    pass
""")

        when:
        AnnotationMetadata metadata = definition.getAnnotationMetadata()

        then:
        metadata.stringValues(TypeHint).size() == 2
        metadata.stringValues(TypeHint)[0] == '[Ljava.util.UUID;'
        metadata.stringValues(TypeHint)[1] == 'java.util.UUID'
    }

    void "test @Nullable on parameter is captured in method parameter metadata"() {
        given:
        BeanDefinition definition = buildBeanDefinition('python', 'Test', '''
from micronaut.core.annotation import Nullable
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
from typing import Annotated

@Singleton
class Test:

    @Executable
    def testMethod(self, name: Annotated[str, Nullable], age: int) -> None:
        pass
''')

        when:
        def method = definition.executableMethods.find { it.methodName == "testMethod" }
        assert method != null

        then:
        method.arguments[0].isNullable()
        !method.arguments[1].isNullable()
    }

    void "test read constants defined at module level"() {
        given:
        BeanDefinition definition = buildBeanDefinition('python', 'Test', '''
from micronaut.context.annotation import Requires
from jakarta.inject import Singleton

TEST = "blah"

@Requires(property=TEST)
@Singleton
class Test:
    pass
''')

        when:
        AnnotationMetadata metadata = definition.getAnnotationMetadata()

        then:
        metadata != null
        metadata.getValue(Requires, "property").isPresent()
        metadata.getValue(Requires, "property").get() == 'TEST'
    }

    void "test annotation metadata with primitive arrays via PrimitiveTypesAnnotation"() {
        given:
        BeanDefinition definition = buildBeanDefinition('python', 'Test', """
from micronaut.python.compiler import PrimitiveTypesAnnotation
from jakarta.inject import Singleton

@PrimitiveTypesAnnotation(doubleArray=[1.1])
@Singleton
class Test:
    pass
""")

        when:
        AnnotationMetadata metadata = definition.getAnnotationMetadata()

        then:
        metadata != null
        // Using the annotation type to query avoids string name mismatches
        metadata.getValue(PrimitiveTypesAnnotation, "doubleArray", double[].class).orElse(new double[0]) == [1.1d] as double[]
        metadata.doubleValue(PrimitiveTypesAnnotation, "doubleArray").asDouble == 1.1d
    }

}
