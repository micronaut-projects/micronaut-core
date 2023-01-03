package io.micronaut.inject.configproperties.eachbeanparameter;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec", value = "EachBeanParameterSpec")
@Named("default")
@Primary
class DefaultDataSource extends AbstractDataSource {
}
