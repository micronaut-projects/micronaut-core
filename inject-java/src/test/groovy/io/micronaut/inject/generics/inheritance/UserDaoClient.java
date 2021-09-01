package io.micronaut.inject.generics.inheritance;

import jakarta.inject.Singleton;

@Singleton
public class UserDaoClient extends DaoClient<User>{
    public UserDaoClient(Dao<User> constructorDao) {
        super(constructorDao);
    }
}
