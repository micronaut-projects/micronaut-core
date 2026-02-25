package io.micronaut.json.bind;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "JsonBeanPropertyBinderDenialOfServiceTest")
@MicronautTest
class JsonBeanPropertyBinderDenialOfServiceTest {

    @Inject
    @Client("/") HttpClient httpClient;

    @ParameterizedTest
    @ValueSource(strings = {
        "authors%5B0%5D.name=low&authors%5B1%5D.name=high",
        "authors%5B1%5D.name=high&authors%5B0%5D.name=low"
    })
    void submittingAFormUrlEncodedPayloadShouldNotCreateAnOutOfMemoryError(String body) {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpRequest<?> request = HttpRequest.POST("/poc/book", body)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        Book b = assertDoesNotThrow(() -> client.retrieve(request, Book.class));
        Book expected = expected();
        assertEquals(expected, b);
    }

    private static Book expected() {
        Book expected = new Book();
        Author firstAuthor = new Author();
        firstAuthor.setName("low");
        Author secondAuthor = new Author();
        secondAuthor.setName("high");
        expected.setAuthors(List.of(firstAuthor, secondAuthor));
        return expected;
    }

    @Requires(property = "spec.name", value = "JsonBeanPropertyBinderDenialOfServiceTest")
    @Controller("/poc")
    static class PocController {
        @Post(uri = "/book", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        Book bind(@Body Book book) { return book; }
    }

    @Introspected
    static class Author {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof Author author)) return false;

            return Objects.equals(name, author.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        @Override
        public String toString() {
            return "Author{" +
                "name='" + name + '\'' +
                '}';
        }
    }

    @Introspected
    static class Book {
        private List<Author> authors;
        public List<Author> getAuthors() { return authors; }
        public void setAuthors(List<Author> authors) { this.authors = authors; }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof Book book)) return false;

            return Objects.equals(authors, book.authors);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(authors);
        }

        @Override
        public String toString() {
            return "Book{" +
                "authors=" + authors +
                '}';
        }
    }
}
