package io.micronaut.docs.inject.anninheritance

//tag::imports[]
import jakarta.inject.Named
import javax.sql.DataSource
//end::imports[]

//tag::class[]
@Named("bookRepository")
class BookRepository(private val dataSource: DataSource) : BaseSqlRepository()
//end::class[]