package io.micronaut.inject.lifecycle.beancreationeventlistener.circular;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Requires(property = "spec", value = "RecursiveListeners")
@Singleton
public class Bar {
}
