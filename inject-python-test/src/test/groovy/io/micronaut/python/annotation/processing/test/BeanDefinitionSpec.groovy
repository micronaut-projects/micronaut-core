/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.annotation.processing.test

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValueProvider
import io.micronaut.core.annotation.Order
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.TypeInformation
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Named
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import spock.lang.PendingFeature
import spock.lang.Unroll

import java.util.function.Function

class BeanDefinitionSpec extends AbstractPythonTypeElementSpec {

    @Unroll
    void "test getTypeString for format #format"() {
        given:
        def definition = buildBeanDefinition("python", "TypeStringService", '''
from jakarta.inject import Singleton

@Singleton
class TypeStringService:
    pass
''')

        expect:
        definition.asArgument().getTypeString(format) == result

        where:
        format                                    | result
        TypeInformation.TypeFormat.SIMPLE         | "TypeStringService"
        TypeInformation.TypeFormat.QUALIFIED      | "python.TypeStringService"
        TypeInformation.TypeFormat.SHORTENED      | "p.TypeStringService"
        TypeInformation.TypeFormat.ANSI_SIMPLE    | "\u001B[0;36mTypeStringService\u001B[0m"
        TypeInformation.TypeFormat.ANSI_QUALIFIED | "\u001B[0;36mpython.TypeStringService\u001B[0m"
        TypeInformation.TypeFormat.ANSI_SHORTENED | "\u001B[0;36mp.TypeStringService\u001B[0m"
    }

    void "test declared generic placeholders from definition"() {
        when:
        def definition = buildBeanDefinition("python", "GenericService", '''
from typing import Generic, TypeVar
from jakarta.inject import Singleton

K = TypeVar("K")
V = TypeVar("V")

@Singleton
class GenericService(Generic[K, V]):
    pass
''')

        then:
        definition.getGenericBeanType().getTypeString(true) == "GenericService<Object, Object>"
    }

    void "test declared generic placeholders from reference"() {
        when:
        def reference = buildBeanDefinitionReference("python", "GenericService", '''
from typing import Generic, TypeVar
from jakarta.inject import Singleton

K = TypeVar("K")
V = TypeVar("V")

@Singleton
class GenericService(Generic[K, V]):
    pass
''')

        then:
        reference.getGenericBeanType().getTypeString(true) == "GenericService<Object, Object>"
    }

    void "test declared generic placeholders from reference with inheritance"() {
        when:
        def reference = buildBeanDefinitionReference("python", "DefaultKafkaConsumerConfiguration", '''
from typing import Generic, TypeVar
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

K = TypeVar("K")
V = TypeVar("V")

class KafkaDefaultConfiguration:
    pass

class AbstractKafkaConfiguration(Generic[K, V]):
    pass

class AbstractKafkaConsumerConfiguration(AbstractKafkaConfiguration[K, V]):
    pass

@Singleton
@Requires(beans=KafkaDefaultConfiguration)
class DefaultKafkaConsumerConfiguration(AbstractKafkaConsumerConfiguration[K, V]):
    pass
''')

        then:
        reference.getGenericBeanType().getTypeString(true) == "DefaultKafkaConsumerConfiguration<Object, Object>"
    }

    void "test limit the exposed bean types"() {
        when:
        def definition = buildBeanDefinition("python", "RunnableService", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Bean

import java

Runnable = java.type("java.lang.Runnable")

@Singleton
@Bean(typed=[Runnable])
class RunnableService(Runnable):
    def run(self):
        pass
''')

        then:
        definition.exposedTypes == [Runnable] as Set
    }

    void "test limit the exposed bean types from reference"() {
        when:
        def reference = buildBeanDefinitionReference("python", "RunnableService", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Bean

import java

Runnable = java.type("java.lang.Runnable")

@Singleton
@Bean(typed=[Runnable])
class RunnableService(Runnable):
    def run(self):
        pass
''')

        then:
        reference.exposedTypes == [Runnable] as Set
    }

    void "test fail compilation on invalid exposed bean type"() {
        when:
        buildBeanDefinition("python", "NotRunnableService", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Bean

import java

Runnable = java.type("java.lang.Runnable")

@Singleton
@Bean(typed=[Runnable])
class NotRunnableService:
    pass
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [java.lang.Runnable] that is not implemented by the bean type")
    }

    void "test order annotation"() {
        given:
        def definition = buildBeanDefinition("python", "OrderedService", '''
from jakarta.inject import Singleton
from micronaut.core.annotation import Order

@Singleton
@Order(10)
class OrderedService:
    pass
''')

        expect:
        definition.intValue(Order).getAsInt() == 10
    }

    @Unroll
    void "test priority annotation maps to order preserving #priority"() {
        given:
        def definition = buildBeanDefinition("python", "PriorityService", """
from jakarta.annotation import Priority
from jakarta.inject import Singleton

@Singleton
@Priority(${priority})
class PriorityService:
    pass
""")

        expect:
        definition.intValue(Order).getAsInt() == priority

        where:
        priority << [10, -3]
    }

    void "test qualifier for named only"() {
        given:
        def definition = buildBeanDefinition("python", "NamedService", '''
from jakarta.inject import Named

@Named("foo")
class NamedService:
    pass
''')

        expect:
        definition.getDeclaredQualifier() == Qualifiers.byName("foo")
    }

    void "test named qualifier from constant"() {
        given:
        def definition = buildBeanDefinition("python", "NamedConstantService", '''
from jakarta.inject import Named, Singleton

NAME = "testing123"

@Named(NAME)
@Singleton
class NamedConstantService:
    pass
''')

        expect:
        definition.stringValue(AnnotationUtil.NAMED).get() == "testing123"
        definition.getDeclaredQualifier() == Qualifiers.byName("testing123")
    }

    void "test synthesize named annotation from bean definition"() {
        given:
        def definition = buildBeanDefinition("python", "NamedService", '''
from jakarta.inject import Named, Singleton

@Named("test")
@Singleton
class NamedService:
    pass
''')

        when:
        def annotation = definition.synthesize(Named, AnnotationUtil.NAMED)

        then:
        annotation.toString() == "@jakarta.inject.Named(value=test)"
        annotation.value() == "test"
        definition.synthesizeDeclared(Named, AnnotationUtil.NAMED).value() == "test"
        annotation instanceof AnnotationValueProvider
        annotation.annotationValue()
    }

    void "test implicit named qualifier on type"() {
        given:
        def definition = buildBeanDefinition("python", "FooBar", '''
from jakarta.inject import Named

@Named
class FooBar:
    pass
''')

        expect:
        definition.stringValue(AnnotationUtil.NAMED).get() == "fooBar"
    }

    void "test implicit named qualifier on type via stereotype"() {
        given:
        def definition = buildBeanDefinition("python", "FooBar", '''
from jakarta.inject import Named, Qualifier, Singleton

@Qualifier
@Named
def Meta(func):
    return func

@Meta
@Singleton
class FooBar:
    pass
''')

        expect:
        definition.stringValue(AnnotationUtil.NAMED).get() == "fooBar"
    }

    void "test no qualifier for scoped bean"() {
        given:
        def definition = buildBeanDefinition("python", "ScopedService", '''
from jakarta.inject import Singleton

@Singleton
class ScopedService:
    pass
''')

        expect:
        definition.getDeclaredQualifier() == null
    }

    void "test singleton enum is rejected as bean"() {
        when:
        buildBeanDefinition("python", "TestEnum", '''
from enum import Enum
from jakarta.inject import Singleton

@Singleton
class TestEnum(Enum):
    ONE = "one"
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Enum types cannot be defined as beans")
    }

    void "test bean definition computed state"() {
        given:
        def definition = buildBeanDefinition("python", "PrimaryService", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Primary

@Singleton
@Primary
class PrimaryService:
    pass
''')

        expect:
        definition != null
        definition.isSingleton()
        !definition.isIterable()
        definition.isPrimary()
        definition.getScopeName().get() == AnnotationUtil.SINGLETON
    }

    void "test qualifier annotation"() {
        given:
        def definition = buildBeanDefinition("python", "QualifiedService", '''
from jakarta.inject import Qualifier

@Qualifier
def MyQualifier(func):
    return func

@MyQualifier
class QualifiedService:
    pass
''')

        expect:
        definition.getDeclaredQualifier() == Qualifiers.byAnnotation(definition.getAnnotationMetadata(), "python.MyQualifier")
        definition.getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).get() == "python.MyQualifier"
    }

    void "test non binding qualifier member metadata"() {
        given:
        def definition = buildBeanDefinition("python", "QualifiedService", '''
from jakarta.inject import Qualifier
from micronaut.context.annotation import NonBinding
from typing import Annotated

@Qualifier
def Cylinders(value: int, description: Annotated[str, NonBinding] = ""):
    def decorator(func):
        return func
    return decorator

@Cylinders(value=8, description="test")
class QualifiedService:
    pass
''')

        expect:
        definition
            .getAnnotationMetadata()
            .getAnnotation(AnnotationUtil.QUALIFIER)
            .stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as Set == ["description", AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        definition
            .annotationMetadata
            .getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).get() == "python.Cylinders"
    }

    @Unroll
    void "test #annotation protocol is not registered as a bean definition"() {
        expect:
        buildBeanDefinition("python", "MyProtocol", """
from typing import Protocol
${importStatement}

@${annotation}
class MyProtocol(Protocol):
    pass
""") == null

        where:
        importStatement                                    | annotation
        "from jakarta.inject import Singleton"             | "Singleton"
        "from micronaut.http.annotation import Controller" | "Controller"
    }

    void "test deep type parameters are created in definition"() {
        given:
        def definition = buildBeanDefinition("python", "DeepGenericService", '''
from jakarta.inject import Singleton

@Singleton
class DeepGenericService:
    def __init__(self, deep_list: list[list[list[str]]]):
        self.deep_list = deep_list
''')

        expect:
        definition != null
        def constructor = definition.getConstructor()

        def param = constructor.getArguments()[0]
        param.getTypeParameters().length == 1
        def param1 = param.getTypeParameters()[0]
        param1.getTypeParameters().length == 1
        def param2 = param1.getTypeParameters()[0]
        param2.getTypeParameters().length == 1
        def param3 = param2.getTypeParameters()[0]
        param3.getType() == String.class
    }

    void "test type arguments for java function interface"() {
        given:
        def definition = buildBeanDefinition("python", "MyFunction", '''
from java.util.function import Function
from jakarta.inject import Singleton

@Singleton
class MyFunction(Function[str, str]):
    def apply(self, value: str) -> str:
        return value
''')

        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == "T"
        definition.getTypeArguments(Function)[1].name == "R"
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == String
    }

    void "test recursive generic type arguments from definition"() {
        given:
        def definition = buildBeanDefinition("python", "RecursiveService", '''
from typing import Generic, TypeVar
from jakarta.inject import Singleton

T = TypeVar("T", bound="RecursiveGeneric")

class RecursiveGeneric(Generic[T]):
    pass

@Singleton
class RecursiveService(RecursiveGeneric["RecursiveService"]):
    pass
''')

        expect:
        definition != null
        definition.getTypeArguments("python.RecursiveGeneric").size() == 1
        definition.getTypeArguments("python.RecursiveGeneric")[0].type.name == "python.RecursiveService"
    }

    void "test generic bean type from factory method"() {
        given:
        def context = buildContext('''\
from typing import Generic, TypeVar
from micronaut.context.annotation import Bean, Factory
from jakarta.inject import Singleton

T = TypeVar("T")

class X(Generic[T]):
    pass

class Y(X):
    pass

@Factory
class TestFactory:
    @Bean
    @Singleton
    def method(self) -> X[Y]:
        return Y()
''')

        when:
        def xType = context.classLoader.loadClass("python.X")
        def definitions = context.getBeanDefinitions(xType)

        then:
        definitions.size() == 1
        definitions.iterator().next().getGenericBeanType().getTypeString(true) == "X<Y>"

        cleanup:
        context.close()
    }

    void "test resolved generic type arguments are not type variables"() {
        given:
        def definition = buildBeanDefinition("python", "TestSerde", '''
from typing import Generic, TypeVar
from jakarta.inject import Singleton

T = TypeVar("T")

class Serializer(Generic[T]):
    pass

class Deserializer(Generic[T]):
    pass

class Serde(Serializer[T], Deserializer[T], Generic[T]):
    pass

@Singleton
class TestSerde(Serde[object]):
    pass
''')

        when:
        def serdeTypeParam = definition.getTypeArguments("python.Serde")[0]
        def serializerTypeParam = definition.getTypeArguments("python.Serializer")[0]
        def deserializerTypeParam = definition.getTypeArguments("python.Deserializer")[0]

        then:
        !serdeTypeParam.isTypeVariable()
        !(serdeTypeParam instanceof GenericPlaceholder)
        !serializerTypeParam.isTypeVariable()
        !(serializerTypeParam instanceof GenericPlaceholder)
        !deserializerTypeParam.isTypeVariable()
        !(deserializerTypeParam instanceof GenericPlaceholder)
    }

    void "test annotation metadata present on deep type parameters of definition"() {
        given:
        def definition = buildBeanDefinition("python", "DeepValidatedService", '''
from typing import Annotated
from jakarta.inject import Singleton
from jakarta.validation.constraints import NotEmpty, NotNull, Size

@Singleton
class DeepValidatedService:
    def __init__(
        self,
        deep_list: list[Annotated[list[Annotated[list[Annotated[str, NotNull]], NotEmpty]], Size(min=1)]]
    ):
        self.deep_list = deep_list
''')

        when:
        def parameter = definition.constructor.arguments[0]
        def firstTypeParameter = parameter.typeParameters[0]
        def secondTypeParameter = firstTypeParameter.typeParameters[0]
        def thirdTypeParameter = secondTypeParameter.typeParameters[0]

        then:
        parameter.annotationMetadata.annotationNames.contains("io.micronaut.validation.annotation.ValidatedElement")
        firstTypeParameter.annotationMetadata.hasAnnotation(Size)
        firstTypeParameter.annotationMetadata.intValue(Size, "min").getAsInt() == 1
        secondTypeParameter.annotationMetadata.hasAnnotation(NotEmpty)
        thirdTypeParameter.annotationMetadata.hasAnnotation(NotNull)
    }
}
