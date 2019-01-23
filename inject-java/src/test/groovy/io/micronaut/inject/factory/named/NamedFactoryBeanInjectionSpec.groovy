package io.micronaut.inject.factory.named

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class NamedFactoryBeanInjectionSpec extends Specification {

    void "test inject named factory beans"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        TestCacheFactory cacheFactory = ctx.getBean(TestCacheFactory)
        RepositoryService repositoryService = ctx.getBean(RepositoryService)

        expect:
        repositoryService.orgRepositoryCache != null
        repositoryService.orgRepositoryCache.is(cacheFactory.orgRepoCache)
        repositoryService.repositoryCache != null
        repositoryService.repositoryCache.is(cacheFactory.repoCache)

        cleanup:
        ctx.close()
    }
}
