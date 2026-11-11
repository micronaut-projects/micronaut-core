package io.micronaut.kotlin.processing.beans.factory

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

class PrimitiveArrayProjectionSpec extends AbstractKotlinCompilerSpec {

    void "a factory method returning a list of a projected primitive array compiles: #projection"(String projection) {
        when:
        def context = buildContext("""
package primitivearrayprojection

import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class ChunksFactory {
    @Singleton
    fun chunks(): MutableList<$projection> = mutableListOf()
}
""")

        then:
        context.getBeanDefinitions(List).any { it.declaringType.get().name == 'primitivearrayprojection.ChunksFactory' }

        cleanup:
        context?.close()

        where:
        projection << ['out CharArray', 'in IntArray']
    }
}
