package io.micronaut.inject.collect;

import javax.inject.Singleton;
import java.util.HashSet;

@Singleton
public class MySetOfStrings extends HashSet<String> {

    public MySetOfStrings() {
        add("foo");
    }
}