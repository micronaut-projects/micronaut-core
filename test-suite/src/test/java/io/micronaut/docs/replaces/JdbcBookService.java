package io.micronaut.docs.replaces;

import io.micronaut.context.annotation.Requires;
import io.micronaut.docs.requires.Book;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// tag::replaces[]
@Singleton
@Requires(beans = DataSource.class)
public class JdbcBookService implements BookService {

    DataSource dataSource;

    public JdbcBookService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

// end::replaces[]

    @Override
    public Book findBook(String title) {
        try(Connection connection = dataSource.getConnection()) {
            PreparedStatement ps = connection.prepareStatement("select * from books where title = ?");
            ps.setString(1, title);
            ResultSet rs = ps.executeQuery();
            if(rs.next()) {
                return new Book(rs.getString("title"));
            }
        }
        catch (SQLException ex) {
            return null;
        }
        return null;
    }
}
