package org.particleframework.inject.concurrency

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

import java.util.concurrent.ConcurrentLinkedQueue

class JavaConcurrentSingleAccessSpec extends Specification {

    void "test that concurrent access to a singleton returns the same object"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        def threads = []
        Collection beans = new ConcurrentLinkedQueue<>()
        30.times {
            threads << Thread.start {
                ConcurrentB b = context.getBean(ConcurrentB)
                beans.add( b )
            }
        }
        for(Thread t in threads) {
            t.join()
        }

        then:
        beans.unique().size() == 1
    }
}
