package demo.micronautdiandroid;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import io.micronaut.context.annotation.Bean;
import java.util.List;
import javax.inject.Inject;

@Bean
public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    @Inject
    BooksFetcher booksFetcher;

    private List<Book> bookList;

    private TextView textViewMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textViewMessage = (TextView) findViewById(R.id.textViewMessage);
        init();
    }

    void init() {
        if ( booksFetcher != null ) {
            booksFetcher.fetchBooks(books -> {
                setBookList(books);
                logBooks();
            });
        } else {
            Log.w(TAG, "Book Fetcher dependency not injected");
            textViewMessage.setText("Book Fetcher dependency not injected");
        }
    }

    void setBookList(List<Book> bookList) {
        this.bookList = bookList;
    }

    void logBooks() {
        if ( bookList != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("Books Fetched #"+bookList.size());
            sb.append("\n");
            for ( Book book : bookList ) {
                sb.append("Book "+book.getTitle());
                sb.append("\n");
            }
            Log.i(TAG, sb.toString());
            textViewMessage.setText(sb.toString());
        }
    }
}
