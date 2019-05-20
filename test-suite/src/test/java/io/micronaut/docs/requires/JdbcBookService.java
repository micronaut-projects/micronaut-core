package io.micronaut.docs.requires;

import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// tag::requires[]
@Singleton
@Requires(beans = DataSource.class)
@Requires(property = "datasource.url")
public class JdbcBookService implements BookService {

    DataSource dataSource;

    public JdbcBookService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

// end::requires[]

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
