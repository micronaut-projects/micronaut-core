package io.micronaut.inject.generics;

import io.micronaut.context.annotation.Bean;

import jakarta.inject.Singleton;

public interface Shop<T> {
}

@Singleton
@Bean(typed = Shop.class)
class BookShop implements Shop<Book> {
}

class Book {}
