/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    void "test inject named factory beans from provider"() {
        given:
        final ApplicationContext context = ApplicationContext.run()
        final ApplicationHelper helper = context.getBean(ApplicationHelper.class)
        Template template = helper.getTemplate()
        expect:
        template != null
    }
}
