package io.micronaut.inject.generics.inheritance;

import javax.inject.Singleton;

@Singleton
public class UserDao extends Dao<User> {
}
