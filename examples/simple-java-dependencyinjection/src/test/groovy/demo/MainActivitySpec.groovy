package demo

import org.particleframework.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MainActivitySpec extends Specification {

    def conditions = new PollingConditions(timeout: 30)

    def "test particle dependency injection works"() {
        when:
        MainActivity activity = ApplicationContext.run(MainActivity)
        activity.init()

        then:
        noExceptionThrown()
        conditions.eventually {
            activity.bookList
            activity.bookList.size() == 1
            activity.bookList[0].title == "Practical Grails 3"
            activity.bookList[1].title == "Grails 3 - Step by Step"
        }
    }
}