package io.micronaut.docs.ioc.mappers;

//tag::mapper[]
import io.micronaut.context.annotation.Mapper.Mapping

interface ChristmasMappers {

    @Mapping(from = "packaging.color", to = "packagingColor")
    @Mapping(from = "#{packaging.weight + present.weight}", to = "weight")
    @Mapping(from = "#{'Merry christmas'}", to = "greetingCard")
    fun merge(packaging: PresentPackaging, present: Present): ChristmasPresent

}
//end::mapper[]
