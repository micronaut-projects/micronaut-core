package io.micronaut.inject.configproperties.eachbeanparameter;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec", value = "EachBeanParameterSpec")
@EachProperty(value = "mydatasources", primary = "default")
class MyDataSource extends AbstractDataSource {
}
