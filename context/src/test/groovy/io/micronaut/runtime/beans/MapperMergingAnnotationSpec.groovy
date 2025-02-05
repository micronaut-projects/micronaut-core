package io.micronaut.runtime.beans

import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Mapper
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MapperMergingAnnotationSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared TestMerge testMerge = context.getBean(TestMerge)

    void "test merge beans"() {
        given:
        var a = new SkeletonPartA(description: "Spooky", age: 120)
        var b = new SkeletonPartB(numBones: 130, height: 184)
        var c = new SkeletonPartC(numToes: 10)

        when:
        Skeleton result = testMerge.merge(a, b, c)

        then:
        result.description == 'Spooky'
        result.age == 120
        result.numBones == 130
        result.height == 184
        result.numToes == 10
    }

    void "test merge beans with mapping"() {
        given:
        var a = new CustomPart(halloweenDescription: "Spooky and gloomy", aliveAge: 100, deadAge: 100)
        var b = new SkeletonPartB(numBones: 130, height: 184)
        var c = new SkeletonPartC(numToes: 10)

        when:
        Skeleton result = testMerge.merge(a, b, c)

        then:
        result.description == 'Spooky and gloomy'
        result.age == 200
        result.numBones == 130
        result.height == 184
        result.numToes == 10
    }

    void "test update from map"() {
        given:
        var s = new Skeleton(description: "Spooky", age: 102)
        var update = ["description": "Boo!!!", "numBones": 200]

        when:
        Skeleton result = testMerge.update(s, update)

        then:
        result.description == 'Boo!!!'
        result.age == 102
        result.numBones == 200
    }

    void "test merge from maps with mapping"() {
        given:
        var a = ["description": "Spooky", deadAge: 100]
        var b = ["halloweenDescription": "Boo!!!", "numBones": 200]

        when:
        Skeleton result = testMerge.merge(a, b)

        then:
        result.description == 'Boo!!!'
        result.age == 100
        result.numBones == 200
    }

    void "test update default merge strategy"() {
        when:
        var skeleton = new Skeleton(description: "Spooky", age: 100, numBones: 12, height: 100, numToes: 2)
        var update = new SkeletonUpdater(halloweenDescription: "Very spooky")

        then:
        testMerge.updateNotNullOverride(skeleton, update) == new Skeleton(description: "Very spooky", age: 100, numBones: 12, height: 100, numToes: 2)
        testMerge.updateAlwaysOverride(skeleton, update) == new Skeleton(description: "Very spooky", height: 100, numToes: 2)
    }

    void "test update custom merge strategy"() {
        when:
        var skeleton = new Skeleton(description: "Spooky", age: 100, numBones: 12, height: 100, numToes: 2)
        var update1 = new SkeletonUpdater(halloweenDescription: "Very spooky", explicitlySet: [])
        var update2 = new SkeletonUpdater(halloweenDescription: "Very spooky", explicitlySet: ["halloweenDescription"])
        var update3 = new SkeletonUpdater(halloweenDescription: "Very spooky", explicitlySet: ["halloweenDescription", "age", "numBones"])

        then:
        // Nothing explicitly set
        testMerge.updateExplicitlySet(skeleton, update1) == skeleton
        // Item was explicitly set
        testMerge.updateExplicitlySet(skeleton, update2) == new Skeleton(description: "Very spooky", age: 100, numBones: 12, height: 100, numToes: 2)
        // Item was explicitly null-ed
        testMerge.updateExplicitlySet(skeleton, update3) == new Skeleton(description: "Very spooky", height: 100, numToes: 2)
    }

}

@Singleton
@Mapper
abstract class TestMerge {

    @Mapper
    abstract Skeleton merge(SkeletonPartA a, SkeletonPartB b, SkeletonPartC c);

    @Mapper.Mapping(from = "a.halloweenDescription", to = "description")
    @Mapper.Mapping(from = "#{a.deadAge + a.aliveAge}", to = "age")
    abstract Skeleton merge(CustomPart a, SkeletonPartB b, SkeletonPartC c);

    @Mapper
    abstract Skeleton update(Skeleton skeleton, Map<String, Object> values);

    @Mapper.Mapping(from = "values.halloweenDescription", to = "description")
    @Mapper.Mapping(from = "#{skeleton.get('deadAge')}", to = "age")
    abstract Skeleton merge(Map<String, Object> skeleton, Map<String, Object> values);

    @Mapper(mergeStrategy = "EXPLICITLY_SET")
    @Mapper.Mapping(from = "updater.halloweenDescription", to = "description")
    abstract Skeleton updateExplicitlySet(Skeleton current, SkeletonUpdater updater)

    @Mapper(mergeStrategy = Mapper.MERGE_STRATEGY_ALWAYS_OVERRIDE)
    @Mapper.Mapping(from = "updater.halloweenDescription", to = "description")
    abstract Skeleton updateAlwaysOverride(Skeleton current, SkeletonUpdater updater)

    @Mapper(mergeStrategy = Mapper.MERGE_STRATEGY_NOT_NULL_OVERRIDE)
    @Mapper.Mapping(from = "updater.halloweenDescription", to = "description")
    abstract Skeleton updateNotNullOverride(Skeleton current, SkeletonUpdater updater)

}

@Introspected
@EqualsAndHashCode
final class Skeleton {
    String description
    Integer age
    Integer numBones
    Float height
    Integer numToes
}

@Introspected
final class SkeletonUpdater {
    String halloweenDescription
    Integer age
    Integer numBones
    Set<String> explicitlySet
}

@Introspected
final class SkeletonPartA {
    String description
    Integer age
}

@Introspected
final class SkeletonPartB {
    Integer numBones
    double height
}

@Introspected
final class SkeletonPartC {
    Integer numToes
}

@Introspected
final class CustomPart {
    String halloweenDescription
    Integer deadAge
    Integer aliveAge
}

@Singleton
@Named("EXPLICITLY_SET")
final class ExplicitlySetMergeStrategy implements Mapper.MergeStrategy {

    @Override
    Object merge(Object currentValue, Object value, Object valueOwner, String propertyName, String mappedPropertyName) {
        if (valueOwner instanceof SkeletonUpdater) {
            return valueOwner.explicitlySet.contains(mappedPropertyName) ? value : currentValue
        } else {
            value
        }
    }
}
