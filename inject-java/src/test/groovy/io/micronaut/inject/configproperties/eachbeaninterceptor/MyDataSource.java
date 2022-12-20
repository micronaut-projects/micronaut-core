package io.micronaut.inject.configproperties.eachbeaninterceptor;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec", value = "EachBeanInterceptorSpec")
@EachProperty(value = "mydatasources", primary = "default")
class MyDataSource {
}
