package org.particleframework.inject.configurations

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.inject.configurations.requiresbean.RequiresBean
import org.particleframework.inject.configurations.requirescondition.TravisBean
import org.particleframework.inject.configurations.requiresconfig.RequiresConfig
import org.particleframework.inject.configurations.requiressdk.RequiresJava9
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
        !context.containsBean(RequiresBean)
        !context.containsBean(RequiresConfig)
        !context.containsBean(RequiresJava9)
        !context.containsBean(TravisBean)
    }
}
