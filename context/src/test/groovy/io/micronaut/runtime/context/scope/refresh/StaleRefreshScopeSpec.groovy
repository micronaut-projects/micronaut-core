package io.micronaut.runtime.context.scope.refresh

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

/**
 * micronaut-test (rebuildContext = true) keeps the RefreshScope of the first context and, after
 * stopping that context and building a new one, still calls onRefreshEvent on the old scope.
 */
class StaleRefreshScopeSpec extends Specification {

    void "a refresh event on the scope of a stopped context does not throw"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        RefreshScope scope = context.getBean(RefreshScope)
        context.stop()

        when:
        scope.onRefreshEvent(new RefreshEvent(Collections.singletonMap("micronaut.test.active.mocks", "changed")))

        then:
        noExceptionThrown()
    }

    void "a scope restarted with refresh handles refresh events again"() {
        given:
        ApplicationContext context = ApplicationContext.run(['foo.bar': 'one'])
        RefreshScope scope = context.getBean(RefreshScope)
        RestartedRefreshableBean bean = context.getBean(RestartedRefreshableBean)

        when:
        scope.refresh()
        String afterRestart = bean.id()
        scope.onRefreshEvent(new RefreshEvent(Collections.singletonMap('foo.bar', 'one')))

        then:
        bean.id() != afterRestart

        cleanup:
        context.close()
    }
}

@io.micronaut.runtime.context.scope.Refreshable('foo')
class RestartedRefreshableBean {
    private final String id = UUID.randomUUID().toString()

    String id() {
        id
    }
}
