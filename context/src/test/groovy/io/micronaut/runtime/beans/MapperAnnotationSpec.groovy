package io.micronaut.runtime.beans


import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Mapper
import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MapperAnnotationSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared Test testBean = context.getBean(Test)

    void testMapConstructorWithMap() {
        given:
        Map<String, Object> map = Map.of(
                "id", "foo",
                "companyId", "bar",
                "parts", 10,
                "token", 123
        )
        SimpleRobotEntity result = testBean.toEntity(map)

        expect:
        result.id == 'foo'
        result.companyId == 'bar'
        result.parts == 10
    }

    void testMapConstructorWithMapTransform() {
        given:
        Map<String, Object> map = Map.of(
                "id", "foo",
                "companyId", "bar",
                "parts", 10,
                "token", 123
        )
        SimpleRobotEntity result = testBean.toEntityTransform(map)

        expect:
        result.id == 'bar'
        result.companyId == 'bar'
        result.parts == 10
    }


    void testBeanMapInstance() {
        given:
        SimpleRobotEntity simpleRobotEntity = new SimpleRobotEntity("foo", "bar")
        simpleRobotEntity.setParts(10)
        simpleRobotEntity.setToken("123")
        SimpleRobotEntity result = testBean.toBuilder(
                simpleRobotEntity
        ).build()

        expect:
        result.id == 'foo'
        result.companyId == 'bar'
        result.parts == 10
    }

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
    abstract SimpleRobotEntity.Builder toBuilder( SimpleRobotEntity entity)

    @Mapper abstract SimpleRobotEntity toEntity(Map<String, Object> map)

    @Mapper.Mapping(to = "id", from = "#{map.get('companyId')}")
    @Mapper abstract SimpleRobotEntity toEntityTransform(Map<String, Object> map)

    @Mapper
    abstract SimpleRobotEntity toEntity(CreateRobot createRobot)

    @Mapper.Mapping(to = "id", from = "#{createRobot.id.toUpperCase()}")
    @Mapper.Mapping(to = "parts", from = "#{createRobot.parts * 2}")
    @Mapper.Mapping(to = "companyId", from = "#{this.calcCompanyId(createRobot)}")
    abstract SimpleRobotEntity toEntityTransform(CreateRobot createRobot)

    String calcCompanyId(CreateRobot createRobot) {
        return createRobot.companyId.reverse()
    }
}

@Introspected
final class CreateRobot {
    final String id
    final String companyId
    final int parts

    CreateRobot(String id, String companyId, int parts) {
        this.id = id
        this.companyId = companyId
        this.parts = parts
    }
}

@Introspected
class SimpleRobotEntity {

    private final String id
    private final String companyId

    private String token

    private int parts

    SimpleRobotEntity(
            String id,
            String companyId) {
        this.id = id
        this.companyId = companyId
    }

    String getId() {
        return id
    }

    String getCompanyId() {
        return companyId
    }

    int getParts() {
        return parts
    }

    void setParts(int parts) {
        this.parts = parts
    }

    String getToken() {
        return token
    }

    void setToken(String token) {
        this.token = token
    }

    @Introspected
    @AccessorsStyle(writePrefixes = "with")
    static class Builder {
        private String id
        private String companyId

        private int parts

        private int token

        Builder withId(String id) {
            this.id = id
            return this
        }

        Builder withParts(int parts) {
            this.parts = parts
            return this
        }

        Builder withToken(int token) {
            this.token = token
            return this
        }


        Builder withCompanyId(String companyId) {
            this.companyId = companyId
            return this
        }

        SimpleRobotEntity build() {
            SimpleRobotEntity simpleRobotEntity = new SimpleRobotEntity(id, companyId)
            simpleRobotEntity.setParts(parts)
            simpleRobotEntity.setToken(String.valueOf(token))
            return simpleRobotEntity
        }
    }

}
