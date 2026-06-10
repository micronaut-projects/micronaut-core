package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.validation.routes.RouteValidationVisitor

class SuspendedReactiveRouteValidationSpec extends AbstractKotlinCompilerSpec {

    private SymbolProcessorProvider routeValidationVisitorProcessorProvider() {
        return { SymbolProcessorEnvironment environment ->
            new TypeElementSymbolProcessor(environment) {
                @Override
                protected Collection<TypeElementVisitor<?, ?>> findTypeElementVisitors() {
                    return [new RouteValidationVisitor()]
                }
            }
        } as SymbolProcessorProvider
    }

    void "test suspended controller with regular return type compiles"() {
        when:
        buildBeanDefinition('test.TestController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/test")
class TestController {
    @Get
    suspend fun hello(): String = "ok"
}
''', [routeValidationVisitorProcessorProvider()])

        then:
        noExceptionThrown()
    }

    void "test suspended controller with reactive return type fails validation"() {
        when:
        buildBeanDefinition('test.TestController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Controller("/test")
class TestController {
    @Get
    suspend fun hello(): Flow<String> = flowOf("ok")
}
''', [routeValidationVisitorProcessorProvider()])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Unsupported suspended controller return type [kotlinx.coroutines.flow.Flow]. Suspend functions must not return reactive or async types.')
    }
}
