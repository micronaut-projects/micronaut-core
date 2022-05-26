package io.micronaut.inject.generics.inheritance;

import jakarta.inject.Singleton;

@Singleton
public class UserDao extends Dao<User> {
}
