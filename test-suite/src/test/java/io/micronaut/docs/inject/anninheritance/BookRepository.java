package io.micronaut.docs.inject.anninheritance;

//tag::imports[]
import jakarta.inject.Named;
import javax.sql.DataSource;
//end::imports[]

//tag::class[]
@Named("bookRepository")
public class BookRepository extends BaseSqlRepository {
    private final DataSource dataSource;

    public BookRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
//end::class[]
