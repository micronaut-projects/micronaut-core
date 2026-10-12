package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class RefreshLazyPropertySourceSpec extends Specification {

    void "a property source that only contributes keys the environment does not have survives a refresh"() {
        given:
        def ctx = ApplicationContext.builder().start()
        Environment env = ctx.environment
        // Mimics micronaut-test-resources' LazyTestResourcesPropertySourceLoader, which contributes
        // only the keys the environment does not already resolve.
        def lazy = new PropertySource() {
            @Override
            String getName() { "lazy" }

            @Override
            Object get(String key) { key == "my.lazy.prop" ? "lazy-value" : null }

            @Override
            Iterator<String> iterator() {
                env.containsProperties("my.lazy.prop") ? Collections.emptyIterator() : ["my.lazy.prop"].iterator()
            }
        }
        env.addPropertySource(lazy)

        expect:
        env.getProperty("my.lazy.prop", String).get() == "lazy-value"

        when:
        def changes = env.refreshAndDiff()

        then:
        env.getProperty("my.lazy.prop", String).isPresent()
        changes.isEmpty()

        cleanup:
        ctx.close()
    }
}
