package io.micronaut.jakartainject.tck.beanimport;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Import;
import junit.framework.TestResult;
import org.atinject.tck.Tck;
import org.atinject.tck.auto.Car;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(
        packages = {"org.atinject.tck.auto", "org.atinject.tck.auto.accessories"},
        annotated = "*")
class BeanImportTck {

    @Test
    void suite() {
        ApplicationContext context = ApplicationContext.run();
        junit.framework.Test test = Tck.testsFor(context.getBean(Car.class), false, true);
        TestResult result = new TestResult();
        test.run(result);
        assertTrue(result.runCount() > 0);
        assertTrue(result.wasSuccessful());
        context.close();
    }
}
