/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS  foo(id INTEGER);");
            connection.createStatement().execute("INSERT INTO foo(id) VALUES (0);");
            connection.commit();
        }


        try (Connection connection = secondary.getConnection()) {
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS foo(id INTEGER);");
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

    @Transactional
    public String longsave(String title) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("UPDATE foo SET id = id + 1;");
            Thread.sleep(10);
            ResultSet resultSet = connection.createStatement().executeQuery("SELECT id FROM foo");
            resultSet.next();
            int value = resultSet.getInt("id");
            return Integer.toString(value);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "";
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