package io.micronaut.inject.generics.inheritance;

import javax.inject.Singleton;

@Singleton
public class JobDaoClient extends DaoClient<Job> {
    public JobDaoClient(Dao<Job> constructorDao) {
        super(constructorDao);
    }
}
