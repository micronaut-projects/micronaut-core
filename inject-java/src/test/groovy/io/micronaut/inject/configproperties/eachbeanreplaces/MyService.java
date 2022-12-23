package io.micronaut.inject.configproperties.eachbeanreplaces;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Requires(property = "spec", value = "EachBeanReplacesSpec")
@EachBean(MyDataSource.class)
@Singleton
@Replaces // This is equivalent to `@Replaces(ClassNotOnClasspath.class)`
class MyService {
}
