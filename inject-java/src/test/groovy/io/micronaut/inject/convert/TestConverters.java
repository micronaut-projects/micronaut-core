package io.micronaut.inject.convert;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Optional;

@Factory
public class TestConverters {

    @Singleton
    TypeConverter<String, Foo> fooConverter() {
        return (object, targetType, context) -> {
            Foo foo = new Foo();
            foo.setName(object);
            return Optional.of(foo);
        };
    }


    static class Foo {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
