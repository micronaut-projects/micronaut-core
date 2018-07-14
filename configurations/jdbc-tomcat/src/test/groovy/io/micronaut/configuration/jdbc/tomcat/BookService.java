package io.micronaut.configuration.jdbc.tomcat;

import io.micronaut.spring.tx.annotation.Transactional;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@Singleton
public class BookService {

    private final DataSource dataSource;
    private final DataSource secondary;

    public BookService(DataSource dataSource, @Named("secondary") DataSource secondary) throws SQLException {
        this.dataSource = dataSource;
        this.secondary = secondary;

        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("CREATE TABLE foo(id INTEGER);");
            connection.createStatement().execute("INSERT INTO foo(id) VALUES (0);");
            connection.commit();
        }


        try (Connection connection = secondary.getConnection()) {
            connection.createStatement().execute("CREATE TABLE foo(id INTEGER);");
            connection.createStatement().execute("INSERT INTO foo(id) VALUES (0);");
            connection.commit();
        }
    }

    @Transactional
    public String save(String title) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("UPDATE foo SET id = id + 1;");

            ResultSet resultSet = connection.createStatement().executeQuery("SELECT id FROM foo");
            resultSet.next();
            int value = resultSet.getInt("id");

            return Integer.toString(value);
        }
    }


    @Transactional("secondary")
    public String saveTwo(String title) throws SQLException {
        try (Connection connection = secondary.getConnection()) {
            connection.createStatement().execute("UPDATE foo SET id = id + 1;");

            ResultSet resultSet = connection.createStatement().executeQuery("SELECT id FROM foo");
            resultSet.next();
            int value = resultSet.getInt("id");

            return Integer.toString(value);
        }
    }
}