package io.micronaut.docs.ioc.introspection

// tag::imports[]
import io.micronaut.core.annotation.Introspected
// end::imports[]

// tag::class[]
@Introspected
class Book {
    private String title
    private String author = 'Ursula Le Guin'

    @Introspected.Property('book_title')
    String title() {
        return title
    }

    @Introspected.Property('book_title')
    void title(String title) {
        this.title = title
    }

    @Introspected.Property(
        value = 'author_name',
        accessKind = [Introspected.Property.Access.READ]
    )
    String author() {
        return author
    }
}
// end::class[]
