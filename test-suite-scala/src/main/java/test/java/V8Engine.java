package test.java;

import io.micronaut.context.annotation.Prototype;

import javax.inject.Named;

@Prototype
@Named("v8")
public class V8Engine extends TestEngine {
    public V8Engine() {
        super(8);
    }
}
