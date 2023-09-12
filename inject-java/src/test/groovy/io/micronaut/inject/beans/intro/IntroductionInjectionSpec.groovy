package io.micronaut.inject.beans.intro

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import spock.lang.Specification

class IntroductionInjectionSpec extends Specification {

    void "test injection"() {
        def ctx = ApplicationContext.run(["spec": "IntroductionInjectionSpec"])
        def cacheString = ctx.getBean(Argument.of(AInterface, String, String))
        def cacheLong = ctx.getBean(Argument.of(AInterface, Long, Long))

        expect:
            cacheString.get("hola", "mundo") == "java.lang.String,java.lang.String"
            cacheLong.get(1L, 2L) == "java.lang.Long,java.lang.Long"

        cleanup:
            ctx.close()
    }
}
