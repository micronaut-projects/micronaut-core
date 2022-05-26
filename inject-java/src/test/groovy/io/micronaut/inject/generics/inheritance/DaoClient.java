package io.micronaut.inject.generics.inheritance;

import jakarta.inject.Inject;

public class DaoClient<T> {

    @Inject
    Dao<T> dao;
    Dao<T> anotherDao;

    final Dao<T> constructorDao;

    public DaoClient(Dao<T> constructorDao) {
        this.constructorDao = constructorDao;
    }

    @Inject
    void setAnotherDao(Dao<T> dao) {
        this.anotherDao = dao;
    }
}
