package io.micronaut.http.server.netty.handler

import io.netty.channel.embedded.EmbeddedChannel
import spock.lang.Specification

class FlushCoalescerSpec extends Specification {
    def 'flushes requested in one turn run once, at the end of the turn'() {
        given:
        def ch = new EmbeddedChannel()
        int flushes = 0
        def coalescer = new FlushCoalescer(ch.eventLoop(), { flushes++ })

        when:
        coalescer.schedule()
        coalescer.schedule()
        coalescer.schedule()
        then:
        coalescer.isScheduled()
        flushes == 0

        when:
        ch.runPendingTasks()
        then:
        !coalescer.isScheduled()
        flushes == 1

        when: 'the next turn'
        coalescer.schedule()
        ch.runPendingTasks()
        then:
        flushes == 2

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'a flush that is performed by the caller cancels the scheduled one'() {
        given:
        def ch = new EmbeddedChannel()
        int flushes = 0
        def coalescer = new FlushCoalescer(ch.eventLoop(), { flushes++ })

        when:
        coalescer.schedule()
        coalescer.cancel()
        ch.runPendingTasks()
        then:
        flushes == 0
        !coalescer.isScheduled()

        when: 'a flush scheduled after the cancel still runs'
        coalescer.schedule()
        ch.runPendingTasks()
        then:
        flushes == 1

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'a scheduled flush can be run early, and then does not run again'() {
        given:
        def ch = new EmbeddedChannel()
        int flushes = 0
        def coalescer = new FlushCoalescer(ch.eventLoop(), { flushes++ })

        when:
        coalescer.flushNow()
        then: 'nothing to flush'
        flushes == 0

        when:
        coalescer.schedule()
        coalescer.flushNow()
        then:
        flushes == 1
        !coalescer.isScheduled()

        when:
        ch.runPendingTasks()
        then:
        flushes == 1

        cleanup:
        ch.finishAndReleaseAll()
    }
}
