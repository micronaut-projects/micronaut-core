package org.particleframework.inject.configurations

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.inject.configurations.test1.RequiresNotABean
import org.particleframework.scope.Context
import spock.lang.Specification

/**
 * Created by graemerocher on 19/05/2017.
 */
class RequiresBeanSpec extends Specification {

    void "test that a configuration can require a bean"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        expect:
        context.containsBean(ABean)
        !context.containsBean(RequiresNotABean)

    }
}
