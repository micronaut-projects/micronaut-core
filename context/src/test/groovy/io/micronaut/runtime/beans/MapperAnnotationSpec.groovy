package io.micronaut.runtime.beans

import io.micronaut.aop.Introduction
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Mapper
import static io.micronaut.context.annotation.Mapper.Mapping
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MapperAnnotationSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared Test testBean = context.getBean(Test)

    void "simple mapper test"() {
        given:
        SimpleRobotEntity result = testBean.toEntity(new CreateRobot("foo", "bar", 10))

        expect:
        result.id == 'foo'
        result.companyId == 'bar'
        result.parts == 10
    }

    void "transform mapper test"() {
        when:
        SimpleRobotEntity result = testBean.toEntityTransform(new CreateRobot("foo", "bar", 10))

        then:
        result.id == 'FOO'
        result.companyId == 'rab'
        result.parts == 20

        when:"a second cached invocation"
        result = testBean.toEntityTransform(new CreateRobot("foo", "bar", 10))

        then:
        result.id == 'FOO'
        result.companyId == 'rab'
        result.parts == 20
    }
}

@Singleton
abstract class Test {
    @Mapper
    abstract SimpleRobotEntity toEntity(CreateRobot createRobot)

    @Mapping(to = "id", from = "#{createRobot.id.toUpperCase()}")
    @Mapping(to = "parts", from = "#{createRobot.parts * 2}")
    @Mapping(to = "companyId", from = "#{this.calcCompanyId(createRobot)}")
    abstract SimpleRobotEntity toEntityTransform(CreateRobot createRobot)

    String calcCompanyId(CreateRobot createRobot) {
        return createRobot.companyId.reverse()
    }
}
