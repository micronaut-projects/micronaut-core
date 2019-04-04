package io.micronaut.docs.config.builder

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import spock.lang.Specification

/**
 * @author Will Buck
 * @since 1.1
 */
class EngineConfigSpec extends Specification {

    void "test EngineConfig provides proper builders"() {
        given: "Application is loaded with some config modifications"
        ApplicationContext applicationContext = new DefaultApplicationContext().run(
                ['my.engine.manufacturer'          : 'Toyota',
                 'my.engine.spark-plug.companyName': 'NGK'
                ],
                'test'
        )

        applicationContext.start()

        EngineConfig engineConfig = applicationContext.getBean(EngineConfig)

        when: "Builder is retrieved with loaded config changes"
        Engine engine = engineConfig.builder.build(engineConfig.crankShaft, engineConfig.sparkPlug)

        then: "Engine combines default and overridden config props"
        engine.start() == "Toyota Engine Starting V0 [rodLength=6.0, sparkPlug=Platinum TT(NGK 4504 PK20TT)]"
    }
}
