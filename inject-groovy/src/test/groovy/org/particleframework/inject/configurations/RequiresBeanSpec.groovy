package org.particleframework.inject.configurations

import org.particleframework.context.ApplicationContext
import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.env.MapPropertySource
import org.particleframework.context.env.PropertySource
import org.particleframework.inject.configurations.requiresbean.RequiresBean
import org.particleframework.inject.configurations.requirescondition.TravisBean
import org.particleframework.inject.configurations.requirescondition2.TrueLambdaBean
import org.particleframework.inject.configurations.requiresconditionfalse.TravisBean2
import org.particleframework.inject.configurations.requiresconditiontrue.TrueBean
import org.particleframework.inject.configurations.requiresconfig.RequiresConfig
import org.particleframework.inject.configurations.requiresproperty.RequiresProperty
import org.particleframework.inject.configurations.requiressdk.RequiresJava9
import spock.lang.Ignore
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
        context.containsBean(TrueBean)
        context.containsBean(TrueLambdaBean)
        !context.containsBean(RequiresBean)
        !context.containsBean(RequiresConfig)
        !context.containsBean(RequiresJava9)
//        !context.containsBean(TravisBean) // TODO: these are broken because closures are not supported for @Requires( condition = {})
//        !context.containsBean(TravisBean2)
    }

    void "test that a condition can be required for a bean when false"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        expect:
        context.containsBean(ABean)
//        !context.containsBean(TravisBean2) // TODO: these are broken because closures are not supported for @Requires( condition = {})
    }

//    @Ignore("it doesn't matter whether TrueEnvCondition returns true or false, context never has TrueBean")
    void "test that a condition can be required for a bean when true"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        expect:
        context.containsBean(ABean)
        context.containsBean(TrueBean)
    }

    void "test that a lambda condition can be required for a bean when true"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        expect:
        context.containsBean(ABean)
        context.containsBean(TrueLambdaBean)
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
        applicationContext.environment.addPropertySource(PropertySource.of(
                'test',
                ['dataSource.url':'jdbc::blah']
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(RequiresProperty)
    }
}
