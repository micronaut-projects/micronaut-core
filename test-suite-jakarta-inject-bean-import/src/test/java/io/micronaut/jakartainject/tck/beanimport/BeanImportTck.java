package io.micronaut.jakartainject.tck.beanimport;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Import;
import junit.framework.Test;
import org.atinject.tck.Tck;
import org.atinject.tck.auto.Car;

@Import(
        packages = {"org.atinject.tck.auto", "org.atinject.tck.auto.accessories"},
        annotated = "*")
public class BeanImportTck {

    public static Test suite() {
        try (BeanContext beanContext = BeanContext.run()) {
            return Tck.testsFor(beanContext.getBean(Car.class), false, true);
        }
    }
}
