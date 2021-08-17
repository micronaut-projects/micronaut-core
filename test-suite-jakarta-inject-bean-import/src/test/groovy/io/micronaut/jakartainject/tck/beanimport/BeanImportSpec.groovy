package io.micronaut.jakartainject.tck.beanimport

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.visitor.BeanImportVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import junit.framework.Test
import junit.framework.TestResult
import org.atinject.tck.Tck
import org.atinject.tck.auto.Car

class BeanImportSpec extends AbstractTypeElementSpec {

    void "test parse bean import"() {
        given:
        def context = buildContext('''
package beanimporttest;

import io.micronaut.context.annotation.Import;

@Import(
        packages = {"org.atinject.tck.auto", "org.atinject.tck.auto.accessories"},
        annotated = "*")
class BeanImportTest {

}
''')
        when:
        Car car = getBean(context, 'org.atinject.tck.auto.Car')
        def test = Tck.testsFor(car, false, true)

        def result = new TestResult()
        test.run(result)

        then:
        result.wasSuccessful()

        cleanup:
        context.close()
    }

}
