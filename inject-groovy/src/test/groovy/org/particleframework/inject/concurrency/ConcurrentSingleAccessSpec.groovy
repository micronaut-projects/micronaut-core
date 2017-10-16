package org.particleframework.inject.concurrency

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

import javax.inject.Singleton
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListSet

/**
 * Created by graemerocher on 17/05/2017.
 */
class ConcurrentSingleAccessSpec extends Specification {

    void "test that concurrent access to a singleton returns the same object"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        def threads = []
        Collection beans = new ConcurrentLinkedQueue<>()
        30.times {
            threads << Thread.start {
                B b = context.getBean(B)
                beans.add( b )
            }
        }
        for(Thread t in threads) {
            t.join()
        }

        then:
        beans.unique().size() == 1
    }

    @Singleton
    static class B {

    }
}
