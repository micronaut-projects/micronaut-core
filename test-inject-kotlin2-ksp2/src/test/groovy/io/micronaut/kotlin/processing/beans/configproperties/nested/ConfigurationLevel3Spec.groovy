package io.micronaut.kotlin.processing.beans.configproperties.nested

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ConfigurationLevel3Spec extends Specification {

    void "multi level configuration"() {
        given:
            ApplicationContext ctx = ApplicationContext.run([
                    'level1.foo': 'abc',
                    'level1.level2.bar': 'xyz',
                    'level1.level2.level3.baz': 'zyx'
            ])
        expect:
            ctx.getBean(ConfigurationLevel1).getFoo() == 'abc'
            ctx.getBean(ConfigurationLevel1.ConfigurationLevel2).getBar() == 'xyz'
            ctx.getBean(ConfigurationLevel1.ConfigurationLevel2.ConfigurationLevel3).getBaz() == 'zyx'
        cleanup:
            ctx.close()
    }
}
