package io.micronaut.docs.inject.generated;

import io.micronaut.test.generated.Example;
import javax.inject.Singleton;

@Singleton
public class MainBean {

    private final Example example;

    public MainBean(Example example) {
        this.example = example;
    }

    public boolean check() {
        return example != null;
    }

}
