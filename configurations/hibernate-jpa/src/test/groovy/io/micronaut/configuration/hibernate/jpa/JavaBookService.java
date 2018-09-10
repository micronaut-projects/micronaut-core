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
}
