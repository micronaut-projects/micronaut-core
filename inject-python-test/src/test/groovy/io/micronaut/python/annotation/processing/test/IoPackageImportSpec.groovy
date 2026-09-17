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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.inject.BeanDefinition
import io.micronaut.python.compiler.InMemoryBeanDefinitionsProvider
import io.micronaut.python.compiler.PyronautCompiler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
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

    void "sub-package alias import of a non-micronaut io.* package is applied at compile time"() {
        when:
        BeanDefinition definition = buildBeanDefinition("python", "PetService", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from io.swagger.v3.oas import annotations as oas
from io.swagger.v3.oas.annotations import tags

@Singleton
@tags.Tag(name="pets")
class PetService:
    @Executable
    @oas.Operation(summary="List pets")
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
    }

    void "modules importing non-micronaut io.* packages load at runtime"() {
        given:
        def context = buildContext("""
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from io.swagger.v3.oas.annotations import Operation
from io.swagger.v3.oas.annotations.tags import Tag as ApiTag
from io.swagger.v3.oas.annotations.media import *
from io.swagger.v3.oas import annotations as oas
import io.swagger.v3.oas.annotations.responses as responses

@Singleton
@ApiTag(name="pets")
class PetService:
    @Executable
    @Operation(summary="List pets")
    @responses.ApiResponse(responseCode="200")
    @oas.Hidden
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
        'from io.swagger.v3.oas import missing as oas'             | 'Cannot resolve Java import [io.swagger.v3.oas.missing]'
        'from io.nosuchpackage.annotations import *'               | 'Cannot resolve Java package [io.nosuchpackage.annotations]'
        'import io.nosuchpackage.annotations as annotations'       | 'Cannot resolve Java package [io.nosuchpackage.annotations]'
        'import io.swagger.v3.oas.annotations'                     | 'Java package import [import io.swagger.v3.oas.annotations] requires an alias such as [import io.swagger.v3.oas.annotations as annotations]'
    }

    void "an io.* annotation whose name is taken by an earlier import needs an alias"() {
        when: "the star import already generated Micronaut's @Header decorator"
        buildBeanDefinition("python", "PetController", '''
from micronaut.http.annotation import *
from io.swagger.v3.oas.annotations.headers import Header

@Controller("/pets")
class PetController:
    @Get
    def list_pets(self) -> str:
        return "pets"
''')

        then: "the clash is reported with an alias suggestion instead of silently binding to the wrong annotation"
        def e = thrown(RuntimeException)
        e.message.contains('Java import [io.swagger.v3.oas.annotations.headers.Header] clashes with the decorator [Header]')
        e.message.contains('[from io.swagger.v3.oas.annotations.headers import Header as SwaggerHeader]')

        when: "the suggested alias is used"
        BeanDefinition definition = buildBeanDefinition("python", "PetController", '''
from micronaut.http.annotation import *
from io.swagger.v3.oas.annotations.headers import Header as SwaggerHeader

@Controller("/pets")
@Header(name="X-Tenant", value="acme")
class PetController:
    @Get
    @SwaggerHeader(name="X-Total-Count")
    def list_pets(self) -> str:
        return "pets"
''')

        then: "both annotations are applied"
        definition != null
        definition.getAnnotationValuesByType(io.micronaut.http.annotation.Header)*.stringValue("name")*.get() == ["X-Tenant"]
        def method = definition.getRequiredMethod("list_pets")
        method.hasAnnotation(Header)
        method.stringValue(Header, "name").get() == "X-Total-Count"
    }

    void "a relative import of an application sub-package named io is not taken for a Java import"() {
        given: "app/io/util.py and app/svc.py importing it relatively"
        def sourceDir = File.createTempDir("python-relative-io", "")
        def appDir = new File(sourceDir, "app")
        def ioDir = new File(appDir, "io")
        ioDir.mkdirs()
        new File(ioDir, "util.py").text = '''\
def helper() -> str:
    return "helped"
'''
        new File(appDir, "svc.py").text = '''\
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from .io.util import helper
from . import io as app_io


@Singleton
class Service:
    @Executable
    def run(self) -> str:
        return helper()

    @Executable
    def run_via_package(self) -> str:
        return app_io.util.helper()
'''
        PythonContextRuntime.resetContext()
        def classLoader = PyronautCompiler.builder()
            .pythonSrc(sourceDir.absolutePath)
            .build()
            .buildClassLoader()
        ApplicationContext context = ApplicationContext.builder()
            .classLoader(classLoader)
            .environments("test")
            .beanDefinitionsProvider(new InMemoryBeanDefinitionsProvider(false))
            .build()
            .start()

        when:
        def service = getBean(context, "app.Service")

        then:
        service.run() == "helped"
        service.run_via_package() == "helped"

        cleanup:
        context?.close()
        sourceDir?.deleteDir()
        PythonContextRuntime.resetContext()
    }
}
