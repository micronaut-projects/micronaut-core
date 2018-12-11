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
package io.micronaut.configuration.hibernate.gorm

import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.transactions.TransactionService
import org.grails.datastore.mapping.validation.ValidationException
import org.grails.orm.hibernate.cfg.Settings
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.annotation.Value
import io.micronaut.context.env.PropertySource
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Shared
import spock.lang.Specification

import javax.annotation.PostConstruct
import javax.inject.Singleton
import javax.sql.DataSource

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GormConfigSpec extends Specification {

    @Shared Map sharedConfig = ['hibernate.cache.use_second_level_cache':true,
                                'hibernate.cache.use_query_cache':false,
                                'hibernate.cache.region.factory_class':'org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory'
    ]

    void "test beans for custom data source"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.build(
                [(Settings.SETTING_DB_CREATE):'create-drop',
                 'dataSource.url':'jdbc:h2:mem:someOtherDb',
                 'dataSource.properties.initialSize':25] + sharedConfig
                )
                .mainClass(GormConfigSpec)
                .start()

        DataSource dataSource = applicationContext.getBean(DataSource).targetDataSource.targetDataSource

        expect:
        dataSource.poolProperties.url == 'jdbc:h2:mem:someOtherDb'
        dataSource.poolProperties.initialSize == 25

        cleanup:
        applicationContext.close()
    }

    void "test gorm configured correctly"() {

        given:
        def config = [(Settings.SETTING_DB_CREATE): 'create-drop'] + sharedConfig
        ApplicationContext applicationContext = ApplicationContext.build(config)
                                                                  .mainClass(GormConfigSpec)
                                                                  .start()

        when:
        TransactionService transactionService = applicationContext.getBean(TransactionService)
        int count = transactionService.withTransaction {
            Book.count
        }

        then:
        applicationContext.containsBean(PlatformTransactionManager)
        applicationContext.containsBean(DataSource)
        count == 0

        when:
        BookService bookService = applicationContext.getBean(BookService)

        then:
        bookService.dbCreate == 'create-drop'
        bookService.list().size() == 0
        bookService.initCalled

        cleanup:
        applicationContext.stop()
    }

    void "test gorm configured correctly again"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment
                .addPackage(getClass().getPackage())
                .addPropertySource(PropertySource.of("test",[(Settings.SETTING_DB_CREATE):'create-drop'] + sharedConfig))
        applicationContext.start()

        when:
        TransactionService transactionService = applicationContext.getBean(TransactionService)
        int count = transactionService.withTransaction {
            Book.count
        }

        then:
        applicationContext.containsBean(PlatformTransactionManager)
        applicationContext.containsBean(DataSource)
        count == 0

        when:
        Book.withNewSession {
            new Book(title: "").save()
        }

        then:
        def e = thrown(ValidationException)

        when:
        count = transactionService.withTransaction {
            new Book(title: "the stand").save()
            Book.count
        }

        then:
        count == 1

        when:
        Book book = transactionService.withTransaction {
            Book.first()
        }

        then:
        book
        book.dateCreated
        book.title == 'the stand'

        when:
        Book updatedBook = transactionService.withTransaction {
            def b = Book.first()
            b.title = 'the shining'
            b.save(flush:true)
        }

        then:
        updatedBook
        updatedBook.dateCreated
        updatedBook.dateCreated < updatedBook.lastUpdated

        cleanup:
        applicationContext.stop()
    }
}

@Entity
class Book {
    String title
    Date dateCreated
    Date lastUpdated

    static constraints = {
        title blank:false
    }

    static mapping = {
        cache true
    }
}

@Service(Book)
@Singleton
abstract class BookService {

    @Value('${data-source.db-create}')
    String dbCreate

    boolean initCalled = false

    @PostConstruct
    void init() {
        initCalled = true
    }

    abstract List<Book> list()
}