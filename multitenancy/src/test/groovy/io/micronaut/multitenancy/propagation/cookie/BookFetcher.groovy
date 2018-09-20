package io.micronaut.multitenancy.propagation.cookie

interface BookFetcher {
    List<String> findAll()
}