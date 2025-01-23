package io.micronaut.runtime.beans


import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Mapper
import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.ConversionService
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MapperAnnotationSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared Test testBean = context.getBean(Test)
    @Shared ConversionService conversionService = context.getBean(ConversionService)

    void "test convert from nested"() {
        given:
        CreateCommand cmd = new CreateCommand(new CreateRobot("foo", "bar", 10), 123)

        when:
        SimpleRobotEntity result = testBean.toEntity(cmd)

        then:
        result.id == 'foo'
        result.companyId == 'bar'
        result.parts == 10
        result.token == "123"

        when:"exercise caching"
        result = testBean.toEntity(cmd)

        then:
        result.id == 'foo'
        result.companyId == 'bar'
        result.parts == 10
        result.token == "123"

        when:"conversion"
        result = conversionService.convertRequired(cmd, SimpleRobotEntity)

        then:
        result.id == 'foo'
        result.companyId == 'bar'
        result.parts == 10
        result.token == "123"
    }

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

    void "converting mapper test"() {
        given:
        SimpleRobotEntity result = testBean.toEntityConvert(new CreateRobot2("foo", "bar", "10"))

        expect:
        result.id == 'FOO'
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

        when:"a doesn't match condition"
        result = testBean.toEntityTransform(new CreateRobot("foo", "bar", 55))

        then:
        result.id == 'FOO'
        result.companyId == 'rab'
        result.parts == 10
    }

    void "list mapper test"() {
        when:
        VacuumCleanersEntity result = testBean.toEntities(
                new VacuumCleanerCollection([
                        new VacuumCleaner("first"),
                        new VacuumCleaner("second"),
                        new VacuumCleaner("third")
                ])
        )

        then:
        result.cleaners[0].name == 'first'
        result.cleaners[1].name == 'second'
        result.cleaners[2].name == 'third'
    }

}

@Singleton
abstract class Test {
    @Mapper
    abstract SimpleRobotEntity.Builder toBuilder( SimpleRobotEntity entity)

    @Mapper abstract SimpleRobotEntity toEntity(Map<String, Object> map)


    @Mapper.Mapping(from = "#{cmd.createRobot}") abstract SimpleRobotEntity toEntity(CreateCommand cmd)

    @Mapper.Mapping(to = "id", from = "#{map.get('companyId')}")
    @Mapper abstract SimpleRobotEntity toEntityTransform(Map<String, Object> map)

    @Mapper
    abstract SimpleRobotEntity toEntity(CreateRobot createRobot)

    @Mapper.Mapping(to = "id", from = "#{createRobot.id.toUpperCase()}")
    @Mapper.Mapping(to = "parts", from = "#{createRobot.parts}")
    abstract SimpleRobotEntity toEntityConvert(CreateRobot2 createRobot)

    @Mapper.Mapping(to = "id", from = "#{createRobot.id.toUpperCase()}")
    @Mapper.Mapping(to = "parts", from = "#{createRobot.parts * 2}", condition = "#{createRobot.parts < 50}", defaultValue = "10")
    @Mapper.Mapping(to = "companyId", from = "#{this.calcCompanyId(createRobot)}")
    abstract SimpleRobotEntity toEntityTransform(CreateRobot createRobot)

    String calcCompanyId(CreateRobot createRobot) {
        return createRobot.companyId.reverse()
    }

    @Mapper
    abstract VacuumCleanersEntity toEntity(VacuumCleaner cleaner)

    @Mapper
    abstract VacuumCleanersEntity toEntities(VacuumCleanerCollection cleaner)
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
final class CreateCommand {
    final CreateRobot createRobot
    final int token

    CreateCommand(CreateRobot createRobot, int token) {
        this.createRobot = createRobot
        this.token = token
    }
}

@Introspected
final class CreateRobot2 {
    final String id
    final String companyId
    final String parts

    CreateRobot2(String id, String companyId, String parts) {
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

@Introspected
final class VacuumCleaner {
    final String name

    VacuumCleaner(String name) {
        this.name = name
    }
}

@Introspected
final class VacuumCleanerEntity {
    final String name

    VacuumCleanerEntity(String name) {
        this.name = name
    }
}

@Introspected
final class VacuumCleanerCollection {
    final List<VacuumCleaner> cleaners

    VacuumCleanerCollection(List<VacuumCleaner> cleaners) {
        this.cleaners = cleaners
    }
}

@Introspected
final class VacuumCleanersEntity {
    final ArrayList<VacuumCleanerEntity> cleaners

    VacuumCleanersEntity(ArrayList<VacuumCleanerEntity> cleaners) {
        this.cleaners = cleaners
    }
}
