package io.micronaut.docs.config.converters

import io.micronaut.context.annotation.ConfigurationProperties
import java.time.LocalDate

@ConfigurationProperties(BookConfigurationProperties.PREFIX)
class BookConfigurationProperties {
    public static final String PREFIX = "myapp"

    protected List<Book> books

    protected LocalDate updatedAt

    List<Book> getBooks() {
        return this.books
    }

    LocalDate getUpdatedAt() {
        return this.updatedAt
    }
}
