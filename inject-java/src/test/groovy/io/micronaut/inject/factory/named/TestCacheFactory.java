package io.micronaut.inject.factory.named;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.reactivex.Flowable;
import io.reactivex.Maybe;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Factory
public class TestCacheFactory {

    private Cache<String, Flowable<String>> orgRepoCache = Caffeine.newBuilder()
            .maximumSize(20)
            .expireAfterWrite(30, TimeUnit.DAYS)
            .build();
    private Cache<String, Maybe<String>> repoCache = Caffeine.newBuilder()
            .maximumSize(20)
            .expireAfterWrite(30, TimeUnit.DAYS)
            .build();

    @Bean
    @Singleton
    @Named("orgRepositoryCache")
    public Cache<String, Flowable<String>> orgRepositoryCache() {
        return orgRepoCache;
    }

    @Bean
    @Singleton
    @Named("repositoryCache")
    public Cache<String, Maybe<String>> repositoryCache() {
        return repoCache;
    }

    public Cache<String, Flowable<String>> getOrgRepoCache() {
        return orgRepoCache;
    }

    public Cache<String, Maybe<String>> getRepoCache() {
        return repoCache;
    }
}
