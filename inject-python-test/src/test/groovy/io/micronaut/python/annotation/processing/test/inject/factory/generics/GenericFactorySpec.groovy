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
package io.micronaut.python.annotation.processing.test.inject.factory.generics

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class GenericFactorySpec extends AbstractPythonTypeElementSpec {

    void "test generic factory with type variables"() {
        given:
        def context = buildContext('''\
from typing import Annotated, Generic, TypeVar
from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Bean, Executable, Factory
import java

ArgumentInjectionPoint = java.type("io.micronaut.inject.ArgumentInjectionPoint")
StringBuilder = java.type("java.lang.StringBuilder")
Float = java.type("java.lang.Float")

K = TypeVar("K")
V = TypeVar("V")

class BaseCache(Generic[K, V]):
    pass

class Cache(BaseCache[K, V], Generic[K, V]):
    pass

class CacheImpl(Cache):
    def __init__(self, key_type, value_type):
        self.key_type = key_type
        self.value_type = value_type

@Singleton
class MyBean:
    field_inject: Annotated[Cache[str, int], Inject] = None
    field_inject_base: Annotated[BaseCache[str, int], Inject] = None
    method_inject: Cache[StringBuilder, Float] = None

    @Inject
    def set_cache(self, method_inject: Cache[StringBuilder, Float]):
        self.method_inject = method_inject

    @Executable
    def field_key_type(self) -> str:
        return self.field_inject.key_type.getName()

    @Executable
    def field_value_type(self) -> str:
        return self.field_inject.value_type.getName()

    @Executable
    def base_field_key_type(self) -> str:
        return self.field_inject_base.key_type.getName()

    @Executable
    def method_key_type(self) -> str:
        return self.method_inject.key_type.getName()

    @Executable
    def method_value_type(self) -> str:
        return self.method_inject.value_type.getName()

@Factory
class CacheFactory:
    @Bean
    def build_cache(self, ip: ArgumentInjectionPoint) -> Cache[K, V]:
        key_type = ip.asArgument().getTypeVariable("K").get().getType()
        value_type = ip.asArgument().getTypeVariable("V").get().getType()
        return CacheImpl(key_type, value_type)
''')

        when:
        def bean = getBean(context, "python.MyBean")

        then:
        bean.field_key_type() == "java.lang.String"
        bean.field_value_type() == "java.lang.Integer"
        bean.base_field_key_type() == "java.lang.String"
        bean.method_key_type() == "java.lang.StringBuilder"
        bean.method_value_type() == "java.lang.Float"

        cleanup:
        context.close()
    }
}
