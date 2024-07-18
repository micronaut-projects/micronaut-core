package io.micronaut.inject.foreach.withenum

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class EnumEachPropertySpec extends Specification {

    void "test each property with enum keys"() {
        given:
        def ctx = ApplicationContext.run(
                "config.summer.cities":"barcelona,atlanta,sydney",
                "config.winter.cities": "albertville,lillehammer,nagano"
        )

        when:
        EnumConfiguration configuration = ctx.getBean(EnumConfiguration, Qualifiers.byName(EnumConfiguration.MyEnum.SUMMER.name()))

        then:
        configuration != null
        configuration.myEnum() == EnumConfiguration.MyEnum.SUMMER


        cleanup:
        ctx.close()
    }
}
