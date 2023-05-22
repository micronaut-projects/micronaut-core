package io.micronaut.inject.configproperties.eachbeanreplaces;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec", value = "EachBeanReplacesSpec")
@EachProperty(value = "mydatasources", primary = "default")
class MyDataSource {
}
