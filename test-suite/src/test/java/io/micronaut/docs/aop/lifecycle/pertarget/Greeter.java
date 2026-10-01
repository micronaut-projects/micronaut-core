package io.micronaut.docs.aop.lifecycle.pertarget;

import io.micronaut.runtime.context.scope.Refreshable;

@Refreshable
@Audited
public class Greeter {

    public String greet(String name) {
        return "Hello " + name;
    }

    public String farewell(String name) {
        return "Goodbye " + name;
    }
}
