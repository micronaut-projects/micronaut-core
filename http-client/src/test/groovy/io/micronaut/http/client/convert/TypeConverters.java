package io.micronaut.http.client.convert;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;

@Factory
public class TypeConverters {

    @Bean
    @Singleton
    public TypeConverter<String, Bar> stringToBarConverter() {
        return TypeConverter.of(
                String.class,
                Bar.class,
                Bar::new
        );
    }

    @Bean
    @Singleton
    public TypeConverter<String, Foo> stringToFooConverter() {
        return TypeConverter.of(
                String.class,
                Foo.class,
                Foo::new
        );
    }

}