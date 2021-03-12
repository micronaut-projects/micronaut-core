package io.micronaut.inject.generics.inheritance;

import javax.inject.Singleton;

@Singleton
public class UserDaoClient extends DaoClient<User>{
    public UserDaoClient(Dao<User> constructorDao) {
        super(constructorDao);
    }
}
