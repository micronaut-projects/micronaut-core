/*
 * Copyright 2017 original authors
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
package org.particleframework.configuration.hibernate.gorm

import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.transactions.TransactionService
import org.grails.orm.hibernate.cfg.Settings
import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.annotation.Value
import org.particleframework.context.env.MapPropertySource
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Ignore
import spock.lang.Specification

import javax.inject.Singleton
import javax.sql.DataSource

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Ignore
class GormConfigSpec extends Specification {

    void "test gorm config configures gorm"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment
                .addPackage(getClass().getPackage())
                .addPropertySource(new MapPropertySource([(Settings.SETTING_DB_CREATE):'create-drop']))
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
        BookService bookService = applicationContext.getBean(BookService)

        then:
//        bookService.dbCreate TODO: fix me!
        bookService.list().size() == 0

        cleanup:
        applicationContext.stop()
    }

    void "test gorm config configures gorm again"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment
                .addPackage(getClass().getPackage())
                .addPropertySource(new MapPropertySource([(Settings.SETTING_DB_CREATE):'create-drop']))
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

        cleanup:
        applicationContext.stop()
    }
}

@Entity
class Book {
    String title
}

@Service(Book)
@Singleton
abstract class BookService {

    @Value('dataSource.dbCreate')
    String dbCreate

    abstract List<Book> list()
}