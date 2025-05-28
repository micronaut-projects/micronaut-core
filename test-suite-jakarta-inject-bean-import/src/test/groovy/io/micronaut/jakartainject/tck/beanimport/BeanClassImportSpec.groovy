package io.micronaut.jakartainject.tck.beanimport

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import junit.framework.Test
import junit.framework.TestResult
import org.atinject.tck.Tck
import org.atinject.tck.auto.Car

class BeanClassImportSpec extends AbstractTypeElementSpec {

    void "test bean import"() {
        given:
        ApplicationContext context = buildContext('''
package beanimporttest;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ClassImport;

@ClassImport(packages = {"org.atinject.tck.auto", "org.atinject.tck.auto.accessories"}, annotate = Bean.class)
class BeanImportTest {
}
''')
        when:
        Car car = getBean(context, 'org.atinject.tck.auto.Car')
        Test test = Tck.testsFor(car, false, true)

        TestResult result = new TestResult()
        test.run(result)

        then:
        result.wasSuccessful()

        cleanup:
        context.close()
    }

    void "test bean import with mixins"() {
        given:
        ApplicationContext context = buildContext('''
package beanimporttest;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ClassImport;
import io.micronaut.context.annotation.Mixin;
import org.atinject.tck.auto.FuelTank;

@Mixin(FuelTank.class)
@Bean
class FuelTankMixin {
}

@ClassImport(packages = {"org.atinject.tck.auto", "org.atinject.tck.auto.accessories"})
class BeanImportTest {
}
''')
        when:
        Car car = getBean(context, 'org.atinject.tck.auto.Car')
        Test test = Tck.testsFor(car, false, true)

        TestResult result = new TestResult()
        test.run(result)

        then:
        result.wasSuccessful()

        cleanup:
        context.close()
    }

}
