package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

/**
 * What a property source sees of the environment while a start or a refresh reads it: the
 * refreshing thread sees the pending state of the refresh, what it has read so far, before any of
 * it is published, and the random values it draws are those later lookups get. A property source is
 * asked for its values and its origin property by property, as it is read.
 */
@Timeout(60)
class RefreshPendingStateSpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13913')
    void "a property source that reads a random port while the environment starts gets the port later lookups return, and after a refresh the port that refresh draws"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('random', ['app.port': '${random.port}'], 1))
        env.addPropertySource(new ReadsPort(env))
        env.start()

        expect:
        env.getRequiredProperty('derived.port', Integer) == env.getRequiredProperty('app.port', Integer)

        when: "the environment is refreshed three times, as a refresh can draw the same port again"
        Integer started = env.getRequiredProperty('app.port', Integer)
        List<List<Integer>> refreshed = (1..3).collect {
            env.refresh()
            [env.getRequiredProperty('app.port', Integer), env.getRequiredProperty('derived.port', Integer)]
        }

        then:
        refreshed.every { it[0] == it[1] }
        refreshed.any { it[0] != started }

        cleanup:
        env.stop()
    }

    void "a property source whose values resolve its own earlier properties gives them, as each is read once the one before is processed: #refresh"() {
        given: "a property source whose url names its host, a host that changes before the refresh"
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        SelfReferencing source = new SelfReferencing(env)
        env.addPropertySource(source)

        when:
        env.start()
        if (refresh == 'refresh') {
            source.host = 'h2'
            env.refresh()
        }

        then:
        env.getProperty('self.url', String).get() == url

        cleanup:
        env.stop()

        where:
        refresh   | url
        'start'   | 'http://h/'
        'refresh' | 'http://h2/'
    }

    void "a property source whose origin is known once it is iterated is given the origin it has once each property is read: #kind, #how"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))

        when:
        if (how == 'registered before the start') {
            env.addPropertySource(new OriginOnceIterated(kind))
            env.start()
        } else {
            env.start()
            env.addPropertySource(new OriginOnceIterated(kind))
        }

        then:
        env.getProperty('app.value', String).get() == 'a'
        kind == 'without properties, failing' || env.getProperty('ob.key', String).get() == 'ob'
        kind == 'without properties, failing' || env.getPropertyEntry('ob.key').get().origin().location() == (kind == 'of the last property read' ? 'origin-of-ob.key' : 'loaded')
        kind != 'of the last property read' || env.getPropertyEntry('ob.other').get().origin().location() == 'origin-of-ob.other'

        cleanup:
        env.stop()

        where:
        [kind, how] << [
                ['without properties, failing', 'failing before iterated', 'placeholder before iterated', 'without a location before iterated', 'of the last property read'],
                ['registered before the start', 'added once it runs']
        ].combinations()
    }

    /**
     * The configuration of an environment that holds the property sources a test gives it and
     * nothing of the machine it runs on.
     */
    private static ApplicationContextConfiguration withoutDefaultPropertySources() {
        ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration
    }

    /**
     * A property source, processed after the one that sets {@code app.port}, whose value is what
     * the environment gives for {@code app.port} when it is read.
     */
    static class ReadsPort implements PropertySource {
        private final Environment env

        ReadsPort(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'derived' }

        @Override
        int getOrder() { 2 }

        @Override
        Object get(String key) {
            key == 'derived.port' ? env.getProperty('app.port', Integer).orElse(null) : null
        }

        @Override
        Iterator<String> iterator() { ['derived.port'].iterator() }
    }

    /**
     * A property source whose url resolves a placeholder of its host, which comes before it and can
     * change, when it is asked for.
     */
    static class SelfReferencing implements PropertySource {
        private final Environment env
        volatile String host = 'h'

        SelfReferencing(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'self' }

        @Override
        Object get(String key) {
            switch (key) {
                case 'self.host': return host
                case 'self.url': return env.placeholderResolver.resolveRequiredPlaceholders('http://${self.host}/')
                default: return null
            }
        }

        @Override
        Iterator<String> iterator() { ['self.host', 'self.url'].iterator() }
    }

    /**
     * A property source whose origin is known once it is iterated, as one that loads a file only then.
     */
    static class OriginOnceIterated implements PropertySource {
        private final String kind
        private volatile String location
        private volatile String lastKey

        OriginOnceIterated(String kind) {
            this.kind = kind
        }

        @Override
        String getName() { 'origin-once-iterated' }

        @Override
        PropertySource.Origin getOrigin() {
            switch (kind) {
                case 'without properties, failing':
                    throw new IllegalStateException('no origin')
                case 'without a location before iterated':
                    return PropertySource.Origin.of(location)
                case 'of the last property read':
                    return PropertySource.Origin.of('origin-of-' + lastKey)
            }
            if (location == null) {
                if (kind == 'failing before iterated') {
                    throw new IllegalStateException('the origin is not known before the property source is loaded')
                }
                return PropertySource.Origin.of('not-loaded')
            }
            return PropertySource.Origin.of(location)
        }

        @Override
        Object get(String key) {
            lastKey = key
            key == 'ob.key' ? 'ob' : (key == 'ob.other' ? 'other' : null)
        }

        @Override
        Iterator<String> iterator() {
            location = 'loaded'
            return kind == 'without properties, failing' ? Collections.emptyIterator() : ['ob.key', 'ob.other'].iterator()
        }
    }
}
