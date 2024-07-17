package io.micronaut.inject.foreach.mapof

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class MapOfNameSpec extends Specification {

    void "test proper name resolution"() {
        given:
            def context = ApplicationContext.run(
                    ["spec.name": "MapOfNameSpec"]
            )
            MainService service = context.getBean(MainService)
            MyBarService barService = context.getBean(MyBarService)
            MyFooService fooService = context.getBean(MyFooService)
            DefaultMyService defaultMyService = context.getBean(DefaultMyService)
        when:
            def services = service.getMyServiceConsumer().getMyServices()

        then:
            services == Map.of(
                    "bar", barService,
                    "foo", fooService,
                    "defaultMyService", defaultMyService
            )

        cleanup:
            context.close()
    }

}
