package io.micronaut.inject.ordered

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NonUniqueBeanException
import jakarta.annotation.Priority
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PriorityOrderingSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void 'test priority selects highest precedence bean'() {
        expect:
        context.getBean(PriorityProduct) instanceof PriorityHighValueProduct
    }

    void 'test priority orders bean collections'() {
        expect:
        context.streamOfType(PriorityProduct).toList()*.class == [
            PriorityHighValueProduct,
            PriorityMidValueProduct,
            PriorityLowValueProduct
        ]
    }

    void 'test duplicate priority remains non unique'() {
        when:
        context.getBean(PriorityFruit)

        then:
        thrown(NonUniqueBeanException)
    }
}

interface PriorityProduct {
}

@Singleton
@Priority(-10)
class PriorityHighValueProduct implements PriorityProduct {
}

@Singleton
class PriorityMidValueProduct implements PriorityProduct {
}

@Singleton
@Priority(10)
class PriorityLowValueProduct implements PriorityProduct {
}

interface PriorityFruit {
}

@Singleton
@Priority(-3)
class PriorityApple implements PriorityFruit {
}

@Singleton
@Priority(-3)
class PriorityBanana implements PriorityFruit {
}
