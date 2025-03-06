package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.http.annotation.Get

class AnnotateArraySpec extends AbstractKotlinCompilerSpec {

    void 'test annotating'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotateArrayClass', '''
package addann;

import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Bean
import io.micronaut.http.annotation.Get


@Bean
class AnnotateArrayClass {

    @Get(
        "/tasks",
        produces = ["application/json"],
        consumes = []
    )
    fun myMethod1(param: MyBean1) : MyBean1? {
        return null
    }

}

class MyBean1
''')
        then:
            definition.getExecutableMethods().iterator().next().stringValues(Get, "consumes") == new String[0]
            definition.getExecutableMethods().iterator().next().getAnnotation(Get).getValues().get("consumes") == new String[0]
    }

}
