package io.micronaut.docs.config.property;

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EngineSpec extends Specification {

     void "test start vehicle with configuration"() {
         given:
         ApplicationContext ctx = ApplicationContext.run(["my.engine.cylinders": "8", "my.engine.manufacturer": "Honda"])
         Engine engine = ctx.getBean(Engine)

         expect:
         engine.manufacturer == "Honda"
         engine.cylinders == 8

         cleanup:
         ctx.close()
    }
}
