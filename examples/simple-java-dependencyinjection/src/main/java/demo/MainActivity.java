package demo;

import io.micronaut.context.annotation.Bean;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.List;

@Bean
public class MainActivity {

    @Inject
    BooksFetcher booksFetcher;

    List<Book> bookList;

    //@PostConstruct
    void init() {
        booksFetcher.fetchBooks(books -> bookList = books);
    }

}
