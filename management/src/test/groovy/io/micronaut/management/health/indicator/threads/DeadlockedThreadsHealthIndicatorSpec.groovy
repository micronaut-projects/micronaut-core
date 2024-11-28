package io.micronaut.management.health.indicator.threads

import io.micronaut.health.HealthStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import spock.lang.Specification

import static java.lang.Thread.sleep

class DeadlockedThreadsHealthIndicatorSpec extends Specification {

    Logger log = LoggerFactory.getLogger(DeadlockedThreadsHealthIndicatorSpec)

    def lock1 = new Object()
    def lock2 = new Object()
    def thread1
    def thread2

    def "No deadlocked threads so status is UP"() {
        given:
        thread1 = new Thread()
        thread2 = new Thread()
        def healthIndicator = new DeadlockedThreadsHealthIndicator()

        when:
        thread1.start()
        thread2.start()
        def result = Mono.from(healthIndicator.getResult()).block()

        then:
        HealthStatus.UP == result.status
        null == result.details
    }

    def "Deadlocked threads found so status is DOWN"() {
        given:
        thread1 = new Thread(() -> {
            synchronized (lock1) {
                log.debug "Thread 1: Holding lock 1"

                sleep 200

                synchronized (lock2) {
                    log.debug "Thread 1: Holding lock 1 and lock 2"
                }
            }
        })
        thread2 = new Thread(() -> {
            synchronized (lock2) {
                log.debug "Thread 2: Holding lock 2"

                sleep 100

                synchronized (lock1) {
                    log.debug "Thread 2: Holding lock 2 and lock 1"
                }
            }
        })
        def healthIndicator = new DeadlockedThreadsHealthIndicator()

        when:
        thread1.start()
        thread2.start()

        Thread.sleep(300)

        def result = Mono.from(healthIndicator.getResult()).block()

        then:
        HealthStatus.DOWN == result.status
        null != result.details
    }
}
