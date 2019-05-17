package io.micronaut.docs.requires

import groovy.sql.Sql
import io.micronaut.context.annotation.Requires

import javax.inject.Singleton
import javax.sql.DataSource
import java.sql.SQLException

// tag::replaces[]
@Singleton
@Requires(beans = DataSource.class)
@Requires(property = "datasource.url")
class JdbcBookService implements BookService {

    DataSource dataSource

// end::replaces[]

    @Override
    Book findBook(String title) {
        try {
            def sql = new Sql(dataSource)
            def result = sql.firstRow("select * from books where title =  $title")
            if(result) {
                return new Book(result.get('title'))
            }
        }
        catch (SQLException ex) {
            return null
        }
        return null
    }
}