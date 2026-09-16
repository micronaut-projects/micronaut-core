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
package io.micronaut.python.annotation.processing.test

import io.micronaut.inject.BeanDefinition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

/**
 * Java packages under {@code io.*} other than {@code io.micronaut} (swagger, kubernetes, ...) collide with
 * Python's stdlib {@code io} module. Every import form must resolve at compile time and load at runtime.
 */
class IoPackageImportSpec extends AbstractPythonTypeElementSpec {

    void "explicit import of a non-micronaut io.* annotation is applied at compile time"() {
        when:
        BeanDefinition definition = buildBeanDefinition("python", "PetService", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from io.swagger.v3.oas.annotations import Operation
from io.swagger.v3.oas.annotations.tags import Tag as ApiTag

@Singleton
@ApiTag(name="pets")
class PetService:
    @Executable
    @Operation(summary="List pets", operationId="listPets")
    def list_pets(self) -> str:
        return "pets"
''')

        then:
        definition != null
        definition.hasAnnotation(Tag)
        definition.stringValue(Tag, "name").get() == "pets"
        def method = definition.getRequiredMethod("list_pets")
        method.hasAnnotation(Operation)
        method.stringValue(Operation, "summary").get() == "List pets"
        method.stringValue(Operation, "operationId").get() == "listPets"
    }

    void "star import of a non-micronaut io.* package is applied at compile time"() {
        when:
        BeanDefinition definition = buildBeanDefinition("python", "PetService", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from io.swagger.v3.oas.annotations import *

@Singleton
class PetService:
    @Executable
    @Operation(summary="List pets")
    def list_pets(self) -> str:
        return "pets"
''')

        then:
        definition != null
        def method = definition.getRequiredMethod("list_pets")
        method.hasAnnotation(Operation)
        method.stringValue(Operation, "summary").get() == "List pets"
    }

    void "module alias import of a non-micronaut io.* package is applied at compile time"() {
        when:
        BeanDefinition definition = buildBeanDefinition("python", "PetService", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
import io.swagger.v3.oas.annotations as oas

@Singleton
class PetService:
    @Executable
    @oas.Operation(summary="List pets")
    def list_pets(self) -> str:
        return "pets"
''')

        then:
        definition != null
        def method = definition.getRequiredMethod("list_pets")
        method.hasAnnotation(Operation)
        method.stringValue(Operation, "summary").get() == "List pets"
    }

    void "modules importing non-micronaut io.* packages load at runtime"() {
        given:
        def context = buildContext("""
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from io.swagger.v3.oas.annotations import Operation
from io.swagger.v3.oas.annotations.tags import Tag as ApiTag
from io.swagger.v3.oas.annotations.media import *
import io.swagger.v3.oas.annotations.responses as responses

@Singleton
@ApiTag(name="pets")
class PetService:
    @Executable
    @Operation(summary="List pets")
    @responses.ApiResponse(responseCode="200")
    def list_pets(self) -> str:
        return "pets"
""")

        when:
        def bean = getBean(context, "python.PetService")

        then:
        bean.list_pets() == "pets"

        cleanup:
        context?.close()
    }

    void "explicit import of a non-micronaut io.* Java class works at compile time and runtime"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from io.swagger.v3.oas.annotations.enums import ParameterIn

@Singleton
class PetService:
    @Executable
    def location(self) -> ParameterIn:
        return ParameterIn.QUERY
''')

        when:
        def bean = getBean(context, "python.PetService")

        then:
        bean.location().toString() == "query"

        cleanup:
        context?.close()
    }

    void "unresolvable io.* imports fail compilation with a clear error"() {
        when:
        buildBeanDefinition("python", "PetService", """
from jakarta.inject import Singleton
${importStatement}

@Singleton
class PetService:
    pass
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains(expectedMessage)

        where:
        importStatement                                            | expectedMessage
        'from io.swagger.v3.oas.annotations import Missing'        | 'Cannot resolve Java import [io.swagger.v3.oas.annotations.Missing]'
        'from io.nosuchpackage.annotations import *'               | 'Cannot resolve Java package [io.nosuchpackage.annotations]'
        'import io.nosuchpackage.annotations as annotations'       | 'Cannot resolve Java package [io.nosuchpackage.annotations]'
    }
}
