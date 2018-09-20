package io.micronaut.multitenancy.propagation.principal

interface BookFetcher {
    List<String> findAll()
}