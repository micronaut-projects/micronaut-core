package io.micronaut.inject.generics.inheritance;

import javax.inject.Singleton;

@Singleton
public class JobDao extends Dao<Job> {
}
