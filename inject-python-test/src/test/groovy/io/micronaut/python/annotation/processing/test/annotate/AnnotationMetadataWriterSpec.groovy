package io.micronaut.python.annotation.processing.test.annotate

import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Requirements
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.TypeHint
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Error
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

    void "test read enum constants from java type references"() {
        given:
        BeanDefinition definition = buildBeanDefinition('python', 'Test', '''
import java
from jakarta.inject import Singleton
from micronaut.http.annotation import Error

HttpStatus = java.type("io.micronaut.http.HttpStatus")

@Singleton
class Test:

    @Error(status=HttpStatus.NOT_FOUND)
    def notFound(self):
        pass
''')

        when:
        def method = definition.getRequiredMethod("notFound")
        def metadata = method.getAnnotationMetadata()

        then:
        metadata.hasAnnotation(Error)
        metadata.getValue(Error, "status", String).get() == "NOT_FOUND"
        metadata.enumValue(Error, "status", HttpStatus).get() == HttpStatus.NOT_FOUND
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

    void "test Python annotation decorator is generated as Java annotation type"() {
        given:
        def context = buildContext('''
from typing import Annotated

from jakarta.inject import Singleton
from micronaut.context.annotation import AliasFor, Executable
from micronaut.core.bind.annotation import Bindable

@Bindable
def ShoppingCart(
    value: Annotated[str, AliasFor(annotation=Bindable, member="value")] = "",
):
    def decorator(func):
        return func
    return decorator

@Singleton
class Test:

    @Executable
    def show(self, sessionId: Annotated[int, ShoppingCart("cartId")]) -> int:
        return sessionId
''')

        when:
        Class<?> shoppingCartType = context.classLoader.loadClass("python.ShoppingCart")
        BeanDefinition definition = getBeanDefinition(context, "python.Test")
        def method = definition.executableMethods.find { it.methodName == "show" }
        def metadata = method.arguments[0].annotationMetadata

        then:
        shoppingCartType.isAnnotation()
        metadata.getAnnotationTypeByStereotype(Bindable).get() == shoppingCartType
        metadata.stringValue(shoppingCartType).get() == "cartId"

        cleanup:
        context?.close()
    }

    void "test Python annotation decorator stereotypes support enum and class values"() {
        given:
        def context = buildContext('''
import java
from typing import Annotated

from micronaut.aop import InterceptorBinding
from micronaut.context.annotation import AnnotationExpressionContext, Requires

DataSource = java.type("javax.sql.DataSource")
InterceptorKind = java.type("io.micronaut.aop.InterceptorKind")

class AnnotationContext:
    pass

class AnnotationMemberContext:
    pass

@Requires(beans=DataSource, classes=AnnotationContext)
@InterceptorBinding(kind=InterceptorKind.POST_CONSTRUCT)
@AnnotationExpressionContext(AnnotationContext)
def CustomAnnotation(
    value: Annotated[str, AnnotationExpressionContext(AnnotationMemberContext)] = "",
):
    def decorator(bean):
        return bean
    return decorator
''')

        when:
        Class<?> annotationType = context.classLoader.loadClass("python.CustomAnnotation")
        def requires = annotationType.getAnnotation(Requires)
        def interceptorBinding = annotationType.getAnnotation(InterceptorBinding)

        then:
        requires.beans()[0].name == "javax.sql.DataSource"
        requires.classes()[0].name == "python.AnnotationContext"
        interceptorBinding.kind() == InterceptorKind.POST_CONSTRUCT

        cleanup:
        context?.close()
    }

    void "test Python annotation decorator same annotation aliases are resolved"() {
        given:
        def context = buildContext('''
from typing import Annotated

from jakarta.inject import Singleton
from micronaut.context.annotation import AliasFor, Executable
from micronaut.core.bind.annotation import Bindable

@Bindable
def NameAuthorization(
    value: Annotated[str, AliasFor(member="name")] = "",
    name: Annotated[str, AliasFor(member="value")] = "",
):
    def decorator(func):
        return func
    return decorator

@Singleton
class Test:

    @Executable
    @NameAuthorization(name="Bob")
    def get(self) -> str:
        return "OK"
''')

        when:
        Class<?> nameAuthorizationType = context.classLoader.loadClass("python.NameAuthorization")
        BeanDefinition definition = getBeanDefinition(context, "python.Test")
        def method = definition.executableMethods.find { it.methodName == "get" }
        def metadata = method.annotationMetadata

        then:
        metadata.stringValue(nameAuthorizationType).get() == "Bob"
        metadata.stringValue(nameAuthorizationType, "name").get() == "Bob"

        cleanup:
        context?.close()
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
