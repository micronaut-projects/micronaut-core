package io.micronaut.configuration.hibernate.jpa;

import io.micronaut.spring.tx.annotation.Transactional;

import javax.persistence.EntityManager;

@Transactional(readOnly = true)
public class JavaReadOnlyBookService {

    private final EntityManager entityManager;

    public JavaReadOnlyBookService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public boolean testNativeQuery() {
        // just testing the method can be invoked
        entityManager.createNativeQuery("select * from book", Book.class).getResultList();
        return true;
    }
}
