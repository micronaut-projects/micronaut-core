package io.micronaut.multitenancy.propagation.httpheader

interface BookFetcher {
    List<String> findAll()
}