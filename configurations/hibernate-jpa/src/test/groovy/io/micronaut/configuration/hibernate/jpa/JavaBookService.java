package io.micronaut.configuration.hibernate.jpa;

import io.micronaut.configuration.hibernate.jpa.scope.CurrentSession;
import io.micronaut.spring.tx.annotation.Transactional;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Singleton
public class JavaBookService {

    @PersistenceContext
    EntityManager entityManagerField;


    private EntityManager entityManager;

    @PersistenceContext
    public void setEntityManager(@CurrentSession EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public boolean testFieldInject() {
        entityManagerField.clear();
        return true;
    }

    @Transactional
    public boolean testMethodInject() {
        entityManager.clear();
        return true;
    }

    @Transactional
    public boolean testNativeQuery() {
        // just testing the method can be invoked
        entityManager.createNativeQuery("select * from book", Book.class).getResultList();
        return true;
    }

    @Transactional
    public boolean testClose() throws Exception {
        // just testing the method can be invoked
        ((AutoCloseable)entityManager).close();
        return true;
    }
}
