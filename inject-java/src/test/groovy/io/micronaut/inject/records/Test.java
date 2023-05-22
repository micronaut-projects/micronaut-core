package io.micronaut.inject.records;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Inject;

import jakarta.validation.constraints.Min;

@Requires(property = "spec.name", value = "RecordBeansSpec")
@ConfigurationProperties("foo")
record Test(
    @Min(20) int num,
    String name,
    @Inject ConversionService conversionService,
    @Inject BeanContext beanContext) {
}
