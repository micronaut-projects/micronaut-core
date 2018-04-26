/*
 * Copyright 2018 original authors
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

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

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
            'jpa.properties.hibernate.hbm2ddl.auto':'create-drop'
    )

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
}

@Entity
class Book {

    @Id
    @GeneratedValue
    Long id

    @NotBlank
    String title
}
