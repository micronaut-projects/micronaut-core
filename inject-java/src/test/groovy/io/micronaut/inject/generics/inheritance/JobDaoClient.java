package io.micronaut.inject.generics.inheritance;

import jakarta.inject.Singleton;

@Singleton
public class JobDaoClient extends DaoClient<Job> {
    public JobDaoClient(Dao<Job> constructorDao) {
        super(constructorDao);
    }
}
