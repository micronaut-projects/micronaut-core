package io.micronaut.inject.collect;

import jakarta.inject.Singleton;
import java.util.Iterator;

@Singleton
public class MyIterable implements Iterable<String> {

    @Override
    public Iterator<String> iterator() {
        return new Iterator<String>() {

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public String next() {
                return null;
            }
        };
    }
}
