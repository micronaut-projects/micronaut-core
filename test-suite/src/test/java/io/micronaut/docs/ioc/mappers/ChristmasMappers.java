package io.micronaut.docs.ioc.mappers;

import io.micronaut.docs.ioc.mappers.ChristmasTypes.ChristmasPresent;
import io.micronaut.docs.ioc.mappers.ChristmasTypes.Present;
import io.micronaut.docs.ioc.mappers.ChristmasTypes.PresentPackaging;

//tag::imports[]
import io.micronaut.context.annotation.Mapper.Mapping;
//end::imports[]

//tag::mapper[]
public interface ChristmasMappers {

    @Mapping(from = "packaging.color", to = "packagingColor")
    @Mapping(from = "#{packaging.weight + present.weight}", to = "weight")
    @Mapping(from = "#{'Merry christmas'}", to = "greetingCard")
    ChristmasPresent merge(PresentPackaging packaging, Present present);

}
//end::mapper[]
