package io.micronaut.scheduling.cron

import spock.lang.Specification

class CronExpressionTest extends Specification {
    void "test toString gives the cron expression as string"() {
        when:
        def cronExpression = CronExpression.create("0 0 12 * * ?")

        then:
        cronExpression.getExpression() == "0 0 12 * * ?"
    }
}
