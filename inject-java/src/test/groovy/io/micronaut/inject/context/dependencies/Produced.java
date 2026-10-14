package io.micronaut.inject.context.dependencies;

public class Produced {
    final Repo repo;

    Produced(Repo repo) {
        this.repo = repo;
    }
}
