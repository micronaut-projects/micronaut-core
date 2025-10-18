package io.micronaut.docs.ioc.mappers

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.ioc.mappers.PrimitiveTypes.PrimitiveMapper
import io.micronaut.docs.ioc.mappers.PrimitiveTypes.SourceWithPrimitive
import io.micronaut.docs.ioc.mappers.PrimitiveTypes.SourceWithWrapper
import io.micronaut.docs.ioc.mappers.PrimitiveTypes.TargetWithPrimitive
import io.micronaut.docs.ioc.mappers.PrimitiveTypes.TargetWithWrapper
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PrimitiveMapperSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(["spec.name": "PrimitiveMapperSpec"])

    void 'test primitive to wrapper mapping'() {
        given:
        PrimitiveMapper mapper = context.getBean(PrimitiveMapper.class)

        when:
        SourceWithPrimitive source = new SourceWithPrimitive()
        source.setId(42L)
        source.setCount(100)
        source.setActive(true)
        source.setScore(95.5f)
        source.setValue(3.14159)

        TargetWithWrapper target = mapper.convert(source)

        then:
        target != null
        target.getId() == Long.valueOf(42L)
        target.getCount() == Integer.valueOf(100)
        target.getActive() == Boolean.valueOf(true)
        target.getScore() == Float.valueOf(95.5f)
        target.getValue() == Double.valueOf(3.14159)
    }

    void 'test wrapper to primitive mapping'() {
        given:
        PrimitiveMapper mapper = context.getBean(PrimitiveMapper.class)

        when:
        SourceWithWrapper source = new SourceWithWrapper()
        source.setId(Long.valueOf(123L))
        source.setCount(Integer.valueOf(200))
        source.setActive(Boolean.valueOf(false))
        source.setScore(Float.valueOf(88.5f))
        source.setValue(Double.valueOf(2.71828))

        TargetWithPrimitive target = mapper.convertToPrimitive(source)

        then:
        target != null
        target.getId() == 123L
        target.getCount() == 200
        target.getActive() == false
        target.getScore() == 88.5f
        target.getValue() == 2.71828
    }
}