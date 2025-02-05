package io.micronaut.docs.ioc.mappers

import io.micronaut.context.annotation.Mapper
import io.micronaut.context.annotation.Mapper.Mapping
import io.micronaut.context.annotation.Mapper.MergeStrategy
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Named
import jakarta.inject.Singleton

// tag::mapper[]
interface AdditionalMappers {

    @Mapper // <1>
    fun merge(packaging: PresentPackaging, present: Present, christmasCard: Card): ChristmasPresent

    @Mapping(
        from = "#{updateFields.get('christmasCard') + '!!'}", to = "greetingCard"
    ) // <2>
    fun update(present: ChristmasPresent, updateFields: Map<String, Any>): ChristmasPresent

    @Mapper(mergeStrategy = "add-numbers") // <3>
    @Mapping(from = "packaging.color", to = "packagingColor")
    fun mergeWithMergeStrategy(packaging: PresentPackaging, present: Present): ChristmasPresent

    @Singleton
    @Named("add-numbers")
    class MyMergeStrategy: MergeStrategy {
        override fun merge(currentValue: Any?, value: Any?, valueOwner: Any, propertyName: String, mappedPropertyName: String): Any? {
            if (currentValue is Float && value is Float) {
                return currentValue + value
            }
            return value
        }
    }

    @Introspected
    data class Card(
        val greetingCard: String
    )

}
// end::mapper[]
