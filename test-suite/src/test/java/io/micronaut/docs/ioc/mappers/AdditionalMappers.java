package io.micronaut.docs.ioc.mappers;

import io.micronaut.context.annotation.Mapper;
import io.micronaut.context.annotation.Mapper.Mapping;
import io.micronaut.context.annotation.Mapper.MergeStrategy;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.docs.ioc.mappers.ChristmasTypes.ChristmasPresent;
import io.micronaut.docs.ioc.mappers.ChristmasTypes.Present;
import io.micronaut.docs.ioc.mappers.ChristmasTypes.PresentPackaging;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Map;

// tag::mapper[]
public interface AdditionalMappers {

    @Mapper // <1>
    ChristmasPresent merge(PresentPackaging packaging, Present present, Card christmasCard);

    @Mapping(
        from = "#{updateFields.get('christmasCard') + '!!'}", to = "greetingCard"
    ) // <2>
    ChristmasPresent update(ChristmasPresent present, Map<String, Object> updateFields);

    @Mapper(
        mergeStrategy = "add-numbers",
        value = {
            @Mapping(from = "packaging.color", to = "packagingColor")
        }
    ) // <3>
    ChristmasPresent mergeWithMergeStrategy(PresentPackaging packaging, Present present);

    @Singleton
    @Named("add-numbers")
    class MyMergeStrategy implements MergeStrategy {
        @Override
        public Object merge(Object currentValue, Object value, Object valueOwner, String propertyName, String mappedPropertyName) {
            if (currentValue instanceof Float a && value instanceof Float b) {
                return a + b;
            }
            return value;
        }
    }

    @Introspected
    record Card(
        String greetingCard
    ) {
    }

}
// end::mapper[]
