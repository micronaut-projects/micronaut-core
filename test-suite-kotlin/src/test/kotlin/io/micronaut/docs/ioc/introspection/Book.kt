package io.micronaut.docs.ioc.introspection

// tag::imports[]
import io.micronaut.core.annotation.Introspected
// end::imports[]

// tag::class[]
@Introspected
class Book {
    private var title: String? = null
    private val author = "Ursula Le Guin"

    @Introspected.Property("book_title")
    fun title(): String? {
        return title
    }

    @Introspected.Property("book_title")
    fun title(title: String?) {
        this.title = title
    }

    @Introspected.Property(
        value = "author_name",
        accessKind = [Introspected.Property.Access.READ]
    )
    fun author(): String {
        return author
    }
}
// end::class[]
