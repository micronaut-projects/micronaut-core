package io.micronaut.inject.configuration;

import javax.inject.Singleton;

@Singleton
@StringArray("${value.list:temp}")
public class StringArrayType {
}
