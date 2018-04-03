package io.micronaut.context.env;


import java.util.Iterator;

public class EmptyPropertySource implements PropertySource {

    private final String name;

    public EmptyPropertySource() {
        this("empty");
    }

    public EmptyPropertySource(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object get(String key) {
        return null;
    }

    @Override
    public Iterator<String> iterator() {
        return new Iterator<String>() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public String next() {
                throw new UnsupportedOperationException("next");
            }
        };
    }
}
