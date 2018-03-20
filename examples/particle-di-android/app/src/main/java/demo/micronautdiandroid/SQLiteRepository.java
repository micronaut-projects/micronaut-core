package demo.micronautdiandroid;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class SQLiteRepository implements BooksFetcher {

    @Override
    public void fetchBooks(OnBooksFetched onBooksFetched) {
        List<Book> bookList = new ArrayList<>();
        bookList.add(new Book("Practical Grails 3"));
        bookList.add(new Book("Grails 3 - Step by Step"));
        if ( onBooksFetched != null ) {
            onBooksFetched.booksFetched(bookList);
        }
    }
}
