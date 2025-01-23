package io.micronaut.docs.ioc.mappers;

//tag::imports[]
import io.micronaut.context.annotation.Mapper.Mapping
//end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "MappersSpec")
//tag::mapper[]
interface ChristmasMappers {

    @Mapping(from = "packaging.color", to = "packagingColor")
    @Mapping(from = "#{packaging.weight + present.weight}", to = "weight")
    @Mapping(from = "#{'Merry christmas'}", to = "greetingCard")
    ChristmasPresent merge(PresentPackaging packaging, Present present)

}
//end::mapper[]
