package io.micronaut.inject.records;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Inject;

import jakarta.validation.constraints.NotNull;

@Requires(property = "spec.name", value = "RecordBeansSpec")
record Test2(
    @Inject @NonNull @NotNull ConversionService conversionService,
    @Inject @NonNull @NotNull BeanContext beanContext) {
}
