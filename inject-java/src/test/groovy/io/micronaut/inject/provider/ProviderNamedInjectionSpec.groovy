package io.micronaut.inject.provider

import io.micronaut.context.ApplicationContext
import org.atinject.tck.auto.DriversSeat
import org.atinject.tck.auto.accessories.SpareTire
import spock.lang.Specification

class ProviderNamedInjectionSpec extends Specification {


    void "test qualified provider injection"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        Seats seats = ctx.getBean(Seats)

        expect:
        seats.driversSeatProvider.get() instanceof DriversSeat
        seats.spareTireProvider.get() instanceof SpareTire
    }
}
