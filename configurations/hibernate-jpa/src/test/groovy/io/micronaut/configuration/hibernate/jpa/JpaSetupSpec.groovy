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
package io.micronaut.configuration.hibernate.jpa

import io.micronaut.configuration.hibernate.jpa.scope.CurrentSession
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanContextException
import io.micronaut.core.util.CollectionUtils
import io.micronaut.http.exceptions.ConnectionClosedException
import io.micronaut.http.exceptions.HttpException
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.spring.tx.annotation.BindableRuleBasedTransactionAttribute
import io.micronaut.spring.tx.annotation.TransactionInterceptor
import io.micronaut.spring.tx.annotation.Transactional
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton
import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.validation.ConstraintViolationException
import javax.validation.constraints.NotBlank

/**
 * @author graemerocher
 * @since 1.0
 */
class JpaSetupSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext applicationContext = ApplicationContext.run(
            'datasources.default.name':'mydb',
            'jpa.default.properties.hibernate.hbm2ddl.auto':'create-drop'
    )

    void "test configure @Transactional attribute"() {
        given:
        BeanDefinition<BookService> beanDefinition = applicationContext.getBeanDefinition(BookService)
        TransactionInterceptor interceptor = applicationContext.getBean(TransactionInterceptor)
        ExecutableMethod method = beanDefinition.findMethod("testMethod").get()

        when:
        BindableRuleBasedTransactionAttribute attribute = interceptor.resolveTransactionAttribute(
                method,
                method.getAnnotationMetadata(),
                "test"
        )

        then:
        attribute != null
        attribute.readOnly
        attribute.timeout == 1000
        attribute.qualifier == 'test'
        attribute.propagationBehavior == TransactionDefinition.PROPAGATION_MANDATORY
        attribute.isolationLevel == TransactionDefinition.ISOLATION_REPEATABLE_READ
        attribute.rollbackFor == CollectionUtils.setOf(BeanContextException)
        attribute.noRollbackFor == CollectionUtils.setOf(HttpException)
        !attribute.rollbackOn(new ConnectionClosedException(""))
        attribute.rollbackOn(new BeanContextException(""))
    }

    void "test setup entity manager with validation"() {
        when:
        EntityManagerFactory entityManagerFactory = applicationContext.getBean(EntityManagerFactory)

        then:
        entityManagerFactory != null

        when:
        EntityManager em = entityManagerFactory.createEntityManager()
        def tx = em.getTransaction()
        tx.begin()
        em.persist(new Book(title: ""))
        em.flush()
        tx.commit()

        then:
        thrown(ConstraintViolationException)
    }

    void "test setup entity manager save entity"() {
        when:
        EntityManagerFactory entityManagerFactory = applicationContext.getBean(EntityManagerFactory)

        then:
        entityManagerFactory != null

        when:
        EntityManager em = entityManagerFactory.createEntityManager()
        def tx = em.getTransaction()
        tx.begin()
        em.persist(new Book(title: "The Stand"))
        em.flush()

        then:
        em.createQuery("select book from Book book").resultList.size() == 1

        cleanup:
        tx.rollback()
    }

    void "test spring based transaction management"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        List<Book> books = bookService.listBooks()

        then:
        books.size() == 0

        when:
        bookService.saveError()

        then:
        def e  = thrown(RuntimeException)

        when:
        books = bookService.listBooks()
        then:
        books.size() == 0

        when:
        bookService.saveSuccess()
        books = bookService.listBooks()

        then:
        books.size() == 1
    }
}

@Entity
class Book {

    @Id
    @GeneratedValue
    Long id

    @NotBlank
    String title
}

@Singleton
class BookService {

    @Inject
    @CurrentSession
    Session session

    @Transactional(
            readOnly = true,
            propagation = Propagation.MANDATORY,
            isolation = Isolation.REPEATABLE_READ,
            transactionManager = "foo",
            timeout = 1000,
            rollbackFor = BeanContextException,
            noRollbackFor = HttpException
    )
    void testMethod() {

    }

    @Transactional(readOnly = true)
    List<Book> listBooks() {
        session.createCriteria(Book).list()
    }

    @Transactional(readOnly = true)
    List<Book> saveReadOnly() {
        session.persist(new Book(title: "the stand"))
        session.createCriteria(Book).list()
    }

    @Transactional()
    List<Book> saveError() {
        session.persist(new Book(title: "the stand"))
        throw new Exception("bad things happened")
    }

    @Transactional()
    List<Book> saveSuccess() {
        session.persist(new Book(title: "the stand"))
        session.createCriteria(Book).list()
    }

}