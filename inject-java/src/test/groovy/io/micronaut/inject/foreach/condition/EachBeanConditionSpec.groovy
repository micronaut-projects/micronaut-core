package io.micronaut.inject.foreach.condition

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class EachBeanConditionSpec extends Specification {

    void "test each bean with custom condition - failure"() {
        given:
        def ctx = ApplicationContext.run(
                'foo.one.enabled':false,
                'foo.two.enabled':false,
                'configs.two.name':'two'
        )

        expect:
        ctx.containsBean(XConfigOne)
        ctx.containsBean(XConfigMany, Qualifiers.byName("two"))
        !ctx.containsBean(XClient, Qualifiers.byName("one"))
        !ctx.containsBean(XClient, Qualifiers.byName("two"))

        cleanup:
        ctx.close()
    }

    void "test each bean with custom condition - success"() {
        given:
        def ctx = ApplicationContext.run(
                'configs.two.enabled':true
        )

        expect:
        ctx.containsBean(XConfigOne)
        ctx.containsBean(XConfigMany, Qualifiers.byName("two"))
        ctx.containsBean(XClient, Qualifiers.byName("one"))
        ctx.containsBean(XClient, Qualifiers.byName("two"))

        cleanup:
        ctx.close()
    }
}
