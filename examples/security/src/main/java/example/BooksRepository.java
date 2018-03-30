package example;

import java.util.List;

public interface BooksRepository {
    List<Book> findAllByGenre(BookGenre genre);
}
