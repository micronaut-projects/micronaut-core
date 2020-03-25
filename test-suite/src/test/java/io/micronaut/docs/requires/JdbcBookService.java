/*
 * Copyright 2017-2020 original authors
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
