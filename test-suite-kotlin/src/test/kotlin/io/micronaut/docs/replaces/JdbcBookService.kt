package io.micronaut.docs.replaces

import io.micronaut.context.annotation.Requires
import io.micronaut.docs.requires.Book

import javax.inject.Singleton
import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

// tag::replaces[]
@Singleton
@Requires(beans = [DataSource::class])
class JdbcBookService(internal var dataSource: DataSource) : BookService {

    // end::replaces[]

    override fun findBook(title: String): Book? {
        try {
            dataSource.connection.use { connection ->
                val ps = connection.prepareStatement("select * from books where title = ?")
                ps.setString(1, title)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    return Book(rs.getString("title"))
                }
            }
        } catch (ex: SQLException) {
            return null
        }

        return null
    }
}
