package io.micronaut.docs.ioc.mappers

import groovy.transform.Canonical;
import io.micronaut.context.annotation.Mapper;
import io.micronaut.context.annotation.Mapper.Mapping;
import io.micronaut.context.annotation.Mapper.MergeStrategy
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "MappersSpec")
// tag::mapper[]
interface AdditionalMappers {

    @Mapper // <1>
    ChristmasPresent merge(PresentPackaging packaging, Present present, Card christmasCard)

    @Mapping(
        from = "#{updateFields.get('christmasCard') + '!!'}", to = "greetingCard"
    ) // <2>
    ChristmasPresent update(ChristmasPresent present, Map<String, Object> updateFields)

    @Mapper(mergeStrategy = "add-numbers") // <3>
    @Mapping(from = "packaging.color", to = "packagingColor")
    ChristmasPresent mergeWithMergeStrategy(PresentPackaging packaging, Present present)

    @Singleton
    @Named("add-numbers")
    class MyMergeStrategy implements MergeStrategy {
        @Override
        Object merge(Object currentValue, Object value, Object valueOwner, String propertyName, String mappedPropertyName) {
            if (currentValue instanceof Float && value instanceof Float) {
                return (Float) (currentValue + value)
            }
            return value
        }
    }

    @Canonical
    @Introspected
    class Card {
        String greetingCard
    }

}
// end::mapper[]
