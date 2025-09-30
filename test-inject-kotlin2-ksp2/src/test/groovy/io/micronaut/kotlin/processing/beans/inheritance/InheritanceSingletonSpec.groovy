package io.micronaut.kotlin.processing.beans.inheritance

import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class InheritanceSingletonSpec extends Specification {

    void "test getBeansOfType returns the same instance"() {
        def ctx = buildContext("""
package test

import jakarta.inject.Singleton

@Singleton
class BankService: AbstractService<String>()

abstract class AbstractService<T>: ServiceContract<T>

interface ServiceContract<T>
""")


        when:
        def bankService = ctx.getBean(ctx.classLoader.loadClass("test.BankService"))
        def otherBankService = ctx.getBeansOfType(ctx.classLoader.loadClass("test.ServiceContract"), Qualifiers.byTypeArgumentsClosest(String))[0]

        then:
        bankService.is(otherBankService)
    }
}
