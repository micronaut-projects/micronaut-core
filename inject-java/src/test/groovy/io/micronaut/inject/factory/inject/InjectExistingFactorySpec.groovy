package io.micronaut.inject.factory.inject

import io.micronaut.context.BeanContext
import spock.lang.Specification

class InjectExistingFactorySpec extends Specification {

    void "test that it is possible inject an existing factory instance without a circular dependency issue"() {

        given:
        MyFactory myFactory = new MyFactory()
        BeanContext ctx = BeanContext.run()

        ctx.inject(myFactory)

        expect:
        myFactory.myService != null

        cleanup:
        ctx.close()
    }
}
