package io.micronaut.inject.beanimport.fixtures;

import jakarta.inject.Singleton;

@Singleton
public class ImportedGenericBean implements GenericInterface<Object> {
}
