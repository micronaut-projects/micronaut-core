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
import io.micronaut.core.annotation.Order
import io.micronaut.core.type.TypeInformation
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.PendingFeature
import spock.lang.Unroll

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

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0001")
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

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0003")
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

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0002")
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
from jakarta.inject import Named, Singleton

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
}
