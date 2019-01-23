package io.micronaut.inject.factory.named;

import com.github.benmanes.caffeine.cache.Cache;
import io.reactivex.Flowable;
import io.reactivex.Maybe;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class RepositoryService {
    private final Cache<String, Flowable<String>> orgRepositoryCache;
    private final Cache<String, Maybe<String>> repositoryCache;

    public RepositoryService(
            @Named("orgRepositoryCache") Cache<String, Flowable<String>> orgRepositoryCache,
            @Named("repositoryCache") Cache<String, Maybe<String>> repositoryCache) {
        this.orgRepositoryCache = orgRepositoryCache;
        this.repositoryCache = repositoryCache;
    }

    public Cache<String, Flowable<String>> getOrgRepositoryCache() {
        return orgRepositoryCache;
    }

    public Cache<String, Maybe<String>> getRepositoryCache() {
        return repositoryCache;
    }
}
