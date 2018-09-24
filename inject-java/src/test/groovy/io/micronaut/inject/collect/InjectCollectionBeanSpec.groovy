package io.micronaut.inject.collect

import io.micronaut.context.BeanContext
import spock.lang.Specification

class InjectCollectionBeanSpec extends Specification {

    void "test resolve collection bean"() {
        given:
        def ctx = BeanContext.run()

        expect:
        ctx.getBean(ThingThatNeedsMySetOfStrings).strings.size() == 1
        ctx.getBean(ThingThatNeedsMySetOfStrings).strings == ctx.getBean(ThingThatNeedsMySetOfStrings).otherStrings
    }
}
