package test.java;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class V8Engine extends TestEngine {
    public V8Engine() {
        super(8);
    }
}
