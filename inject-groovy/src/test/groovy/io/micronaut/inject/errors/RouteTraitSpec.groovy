package io.micronaut.inject.errors

import groovy.transform.NotYetImplemented
import io.micronaut.AbstractBeanDefinitionSpec
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
