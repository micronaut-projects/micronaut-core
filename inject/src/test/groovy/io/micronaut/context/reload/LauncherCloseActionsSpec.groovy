package io.micronaut.context.reload

import spock.lang.Specification

class LauncherCloseActionsSpec extends Specification {

    void cleanup() {
        LauncherCloseActions.runAll()
    }

    void "the releases run once when the launcher closes, a failing one not keeping the next from running"() {
        given:
        List<String> released = []
        LauncherCloseActions.register('spec.first', { released << 'first, replaced' } as Runnable)
        LauncherCloseActions.register('spec.first', { released << 'first' } as Runnable)
        LauncherCloseActions.register('spec.failing', { throw new IllegalStateException('broken') } as Runnable)
        LauncherCloseActions.register('spec.last', { released << 'last' } as Runnable)

        when:
        LauncherCloseActions.runAll()

        then: 'a later registration under a key replaces the earlier one'
        released == ['first', 'last']

        when: 'they are forgotten once run'
        LauncherCloseActions.runAll()

        then:
        released == ['first', 'last']
    }
}
