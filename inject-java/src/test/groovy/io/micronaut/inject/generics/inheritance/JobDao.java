package io.micronaut.inject.generics.inheritance;

import jakarta.inject.Singleton;

@Singleton
public class JobDao extends Dao<Job> {
}
