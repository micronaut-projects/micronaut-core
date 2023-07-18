package io.micronaut.runtime.beans

import io.micronaut.runtime.beans.SimpleRobotEntity.TestBeans
import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanMapper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.Introspected

class BeanMapHandlerSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext ctx = ApplicationContext.run()
    @Shared TestBeans testBeans = ctx.getBean(TestBeans)

    void testMapConstructor() {
        given:
        SimpleRobotEntity result = testBeans.createToEntity.map(new CreateRobot("foo", "bar", 10), SimpleRobotEntity.class)

        expect:
        result.id == 'foo'
        result.companyId == 'bar'
        result.parts == 10
    }

    void testMapConstructorWithMap() {
        given:
        Map<String, Object> map = Map.of(
                "id", "foo",
                "companyId", "bar",
                "parts", 10,
                "token", 123
        )
        SimpleRobotEntity result = testBeans.mapToEntity.map(map, SimpleRobotEntity.class)

        expect:
        result.id == 'foo'
        result.companyId == 'bar'
        result.parts == 10
    }

    void testBeanMapTypeWithMap() {
        given:
        Map<String, Object> map = Map.of(
                "id", "foo",
                "companyId", "bar",
                "parts", 10,
                "token", 123
        )
        SimpleRobotEntity.Builder result = testBeans.mapToBuilder.map(
                map,
                SimpleRobotEntity.Builder.class
        )
        SimpleRobotEntity built = result.build()

        expect:
        built.id == 'foo'
        built.companyId == 'bar'
        built.parts == 10
        built.token == '123'
    }

    void testBeanMapType() {
        given:
        SimpleRobotEntity simpleRobotEntity = new SimpleRobotEntity("foo", "bar")
        simpleRobotEntity.setParts(10)
        simpleRobotEntity.setToken("123")
        SimpleRobotEntity.Builder result = testBeans.entityToBuilder.map(
                simpleRobotEntity,
                SimpleRobotEntity.Builder.class,
                BeanMapper.MapStrategy.DEFAULT
        )

        SimpleRobotEntity built = result.build()

        expect:
        built.id == 'foo'
        built.companyId == 'bar'
        built.parts == 10
        built.token == '123'

    }

    void testBeanMapInstance() {
        given:
        SimpleRobotEntity simpleRobotEntity = new SimpleRobotEntity("foo", "bar")
        simpleRobotEntity.setParts(10)
        simpleRobotEntity.setToken("123")
        SimpleRobotEntity.Builder builder = new SimpleRobotEntity.Builder()
        SimpleRobotEntity result = testBeans.entityToBuilder.map(
                simpleRobotEntity,
                builder,
                BeanMapper.MapStrategy.DEFAULT
        ).build()

        expect:
        result.id == 'foo'
        result.companyId == 'bar'
        result.parts == 10
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

    @Singleton
    static class TestBeans {
        @Inject
        BeanMapper<CreateRobot, SimpleRobotEntity> createToEntity
        @Inject
        BeanMapper<Map<String, Object>, SimpleRobotEntity> mapToEntity
        @Inject
        BeanMapper<Map<String, Object>, SimpleRobotEntity.Builder> mapToBuilder
        @Inject
        BeanMapper<SimpleRobotEntity, SimpleRobotEntity.Builder> entityToBuilder
    }
}
