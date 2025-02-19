package io.micronaut.inject.foreach.qualifier

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EachBeanQualifierSpec extends Specification {

    void "test returning a map of beans"() {
        given:
            ApplicationContext context = ApplicationContext.run([
                    'spec': 'EachBeanQualifierSpec'
            ])
        when:
            def bean = context.getBean(MyMapEachUser)
        then:
            bean.getAllMap().keySet().containsAll(["Foo", "Bar"])
        cleanup:
            context.close()
    }

    void "test mapping each bean with qualifier"() {
        given:
            ApplicationContext context = ApplicationContext.run([
                    'spec': 'EachBeanQualifierSpec'
            ])
        when:
            def myEach1Users = context.getBean(MyEach1User)
        then:
            myEach1Users.getAll().size() == 2
            myEach1Users.getAll().collect { it.myService.name }.containsAll("foo", "bar")
        when:
            def myEach1s = context.getBeansOfType(MyEach1)
        then:
            myEach1s.size() == 2
            myEach1s.collect { it.myService.name }.containsAll("foo", "bar")

        when:
            def myEach2Users = context.getBean(MyEach2User)
        then:
            myEach2Users.getAll().size() == 2
            myEach2Users.getAll().collect { it.myServiceBeanReg.bean().name }.containsAll("foo", "bar")
            myEach2Users.getAll().collect { context.getBean(it.myServiceBeanReg.definition()).name }.containsAll("foo", "bar")
        when:
            def myEach2s = context.getBeansOfType(MyEach2)
        then:
            myEach2s.size() == 2
            myEach2s.collect { it.myServiceBeanReg.bean().name }.containsAll("foo", "bar")
            myEach2s.collect { context.getBean(it.myServiceBeanReg.definition()).name }.containsAll("foo", "bar")

        when:
            def myEach3Users = context.getBean(MyEach3User)
        then:
            myEach3Users.getAll().size() == 2
            myEach3Users.getAll().collect { it.myServiceBeanReg.bean().name }.containsAll("foo", "bar")
            myEach3Users.getAll().collect { context.getBean(it.myServiceBeanReg.definition()).name }.containsAll("foo", "bar")
            myEach3Users.getAll().collect { it.qualifier.toString() }.containsAll("@Named('Foo')", "@Named('Bar')")
        when:
            def myEach3s = context.getBeansOfType(MyEach3)
        then:
            myEach3s.size() == 2
            myEach3s.collect { it.myServiceBeanReg.bean().name }.containsAll("foo", "bar")
            myEach3s.collect { context.getBean(it.myServiceBeanReg.definition()).name }.containsAll("foo", "bar")
            myEach3s.collect { it.qualifier.toString() }.containsAll("@Named('Foo')", "@Named('Bar')")

        cleanup:
            context.close()
    }
}
