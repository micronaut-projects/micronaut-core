package io.micronaut.management.health.indicator.threads

import io.micronaut.context.ApplicationContext
import io.micronaut.health.HealthStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import spock.lang.Specification

import static java.lang.Thread.sleep

class DeadlockedThreadsHealthIndicatorSpec extends Specification {

    Logger log = LoggerFactory.getLogger(DeadlockedThreadsHealthIndicatorSpec)

    def "No deadlocked threads so status is UP"() {
        given:
        ApplicationContext applicationContext  = ApplicationContext.run()
        Thread thread1 = new Thread()
        Thread thread2 = new Thread()
        DeadlockedThreadsHealthIndicator healthIndicator = applicationContext.getBean(DeadlockedThreadsHealthIndicator)
        when:
        thread1.start()
        thread2.start()
        def result = Mono.from(healthIndicator.getResult()).block()

        then:
        HealthStatus.UP == result.status
        null == result.details

        cleanup:
        applicationContext.close()
    }

    def "Deadlocked threads found so status is DOWN"() {
        given:
        ApplicationContext applicationContext  = ApplicationContext.run()
        Object lock1 = new Object()
        Object lock2 = new Object()
        Thread thread1 = new Thread(() -> {
            synchronized (lock1) {
                log.debug "Thread 1: Holding lock 1"

                sleep 200

                synchronized (lock2) {
                    log.debug "Thread 1: Holding lock 1 and lock 2"
                }
            }
        })
        Thread thread2 = new Thread(() -> {
            synchronized (lock2) {
                log.debug "Thread 2: Holding lock 2"

                sleep 100

                synchronized (lock1) {
                    log.debug "Thread 2: Holding lock 2 and lock 1"
                }
            }
        })
        DeadlockedThreadsHealthIndicator healthIndicator = applicationContext.getBean(DeadlockedThreadsHealthIndicator)

        when:
        thread1.start()
        thread2.start()

        Thread.sleep(300)

        def result = Mono.from(healthIndicator.getResult()).block()

        then:
        HealthStatus.DOWN == result.status
        null != result.details

        cleanup:
        applicationContext.close()
    }
}
