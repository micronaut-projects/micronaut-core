package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.EachProperty;

@EachProperty("foo.bar")
public interface InterfaceConfig {

    String getHost();

    int getPort();
}
