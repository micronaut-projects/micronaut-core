package io.micronaut.docs.ioc.mappers

import io.micronaut.context.annotation.Mapper
import io.micronaut.context.annotation.Mapper.Mapping
import io.micronaut.context.annotation.Mapper.MergeStrategy
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.ReflectiveAccess
import io.micronaut.docs.ioc.mappers.ChristmasTypes.ChristmasPresent
import io.micronaut.docs.ioc.mappers.ChristmasTypes.Present
import io.micronaut.docs.ioc.mappers.ChristmasTypes.PresentPackaging
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

    @Mapper(
        mergeStrategy = "add-numbers",
        value = [
            Mapping(from = "packaging.color", to = "packagingColor")
        ]
    ) // <3>
    fun mergeWithMergeStrategy(packaging: PresentPackaging, present: Present): ChristmasPresent

    @Singleton
    @Named("add-numbers")
    class MyMergeStrategy : MergeStrategy {
        override fun merge(currentValue: Any?, value: Any?, valueOwner: Any, propertyName: String, mappedPropertyName: String): Any? {
            if (currentValue is Float && value is Float) {
                return currentValue + value
            }
            return value
        }
    }

    @ReflectiveAccess
    @Introspected
    data class Card(
        val greetingCard: String
    )
}
// end::mapper[]
