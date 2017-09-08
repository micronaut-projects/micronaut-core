package org.particleframework.inject.configurations

import org.particleframework.context.ApplicationContext
import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.env.MapPropertySource
import org.particleframework.inject.configurations.requiresbean.RequiresBean
import org.particleframework.inject.configurations.requirescondition.TravisBean
import org.particleframework.inject.configurations.requiresconditionclass.TravisBean2
import org.particleframework.inject.configurations.requiresconfig.RequiresConfig
import org.particleframework.inject.configurations.requiresproperty.RequiresProperty
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
        !context.containsBean(TravisBean2)
    }

    void "test requires property when not present"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.start()

        expect:
        !applicationContext.containsBean(RequiresProperty)
    }

    void "test requires property when present"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(new MapPropertySource(
                'dataSource.url':'jdbc::blah'
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(RequiresProperty)
    }
}
