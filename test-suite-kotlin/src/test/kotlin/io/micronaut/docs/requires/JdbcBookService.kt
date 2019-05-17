package io.micronaut.docs.requires

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires

import javax.inject.Singleton
import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

// tag::requires[]
@Singleton
@Requirements(Requires(beans = [DataSource::class]), Requires(property = "datasource.url"))
class JdbcBookService(internal var dataSource: DataSource) : BookService {
// end::requires[]

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
