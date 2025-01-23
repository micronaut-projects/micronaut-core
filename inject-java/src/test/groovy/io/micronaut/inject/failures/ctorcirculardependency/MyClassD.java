package io.micronaut.inject.failures.ctorcirculardependency;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "ConstructorCircularDependencyFailureSpec")
@Singleton
public class MyClassD {

    @Inject
    public MyClassD(MyClassB propB) {}

}
