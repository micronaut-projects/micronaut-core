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
package io.micronaut.inject.errors


import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.http.annotation.Get
import spock.lang.Issue

class RouteTraitSpec extends AbstractBeanDefinitionSpec {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1103')
    void "test that routes inherited from traits compile"() {
        given:
        def definition = buildBeanDefinition('test.HelloController', '''
package test
import io.micronaut.http.annotation.*
import io.micronaut.http.*

@groovy.transform.CompileStatic
@Controller("/hello")
class HelloController implements ControllerTrait {
  @Get("/{x}")
  @Produces(MediaType.TEXT_PLAIN)
  String index(String x) {
      "Hello World ${x}"
  }
}
trait ControllerTrait {
  @Get("/trait/{x}")
  @Produces(MediaType.TEXT_PLAIN)
  String indexT(String x) {
      "Hello World Trait ${x}"
  }
}
''')

        expect:
        definition != null
        definition.executableMethods.size() == 2
        definition.executableMethods.find { it.methodName == 'indexT'}
        definition.executableMethods.find { it.methodName == 'indexT'}.arguments.length == 1
        definition.executableMethods.find { it.methodName == 'indexT'}.isAnnotationPresent(Get)
        definition.executableMethods.find { it.methodName == 'indexT'}.arguments[0].name == 'x'
    }
}
