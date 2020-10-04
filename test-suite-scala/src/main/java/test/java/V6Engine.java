package test.java;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class V6Engine extends TestEngine {
    public V6Engine() {
        super(6);
    }
}
