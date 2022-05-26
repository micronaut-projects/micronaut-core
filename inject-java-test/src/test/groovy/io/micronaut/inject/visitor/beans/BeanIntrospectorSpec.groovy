package io.micronaut.inject.visitor.beans

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.ReflectiveAccess
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.PendingFeature
import spock.lang.Specification

import jakarta.inject.Singleton
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Version

class BeanIntrospectorSpec extends Specification {

    void "test getIntrospection"() {
        when:
        BeanIntrospection<TestBean> beanIntrospection = BeanIntrospector.SHARED.getIntrospection(TestBean)

        then:
        beanIntrospection
        beanIntrospection.instantiate() instanceof TestBean
        beanIntrospection.propertyNames.size() == 4

        and:"You get a unique instance per call"
        BeanIntrospection.getIntrospection(TestBean).instantiate() instanceof TestBean
        !beanIntrospection.is(BeanIntrospection.getIntrospection(TestBean))

        when:
        def flagProp = beanIntrospection.getRequiredProperty("flag", boolean.class)
        def testBean = beanIntrospection.instantiate()
        flagProp.set(testBean, true)

        then:
        flagProp.get(testBean)
        testBean.flag

    }

    void "test entity"() {
        given:
        BeanIntrospection<TestEntity> introspection = BeanIntrospection.getIntrospection(TestEntity)

        expect:
        introspection.hasAnnotation(ReflectiveAccess)
        introspection.getProperty("id").get().hasAnnotation(Id)
        !introspection.getProperty("id").get().hasAnnotation(Entity)
        !introspection.getProperty("id").get().hasStereotype(Entity)

        introspection.getProperty("version").get().hasAnnotation(Version)
        !introspection.getProperty("version").get().hasAnnotation(Entity)
        !introspection.getProperty("version").get().hasStereotype(Entity)
    }

    void "test find introspections"() {
        expect:
        BeanIntrospector.SHARED.findIntrospections(Introspected).size() > 0
        BeanIntrospector.SHARED.findIntrospections(Introspected, "io.micronaut.inject.visitor.beans").size() == 5
        BeanIntrospector.SHARED.findIntrospections(Introspected, "blah").size() == 0
    }

    @PendingFeature
    void "test instantiating with a non null argument with null"() {
        BeanIntrospection<NonNullBean> introspection = BeanIntrospection.getIntrospection(NonNullBean)

        when:
        introspection.instantiate(false, [null] as Object[])

        then:
        def ex = thrown(IllegalArgumentException)
    }
}
