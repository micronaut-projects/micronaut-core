package io.micronaut.function.client.aws

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/525")
@Narrative("""
The beans participant in this tests are displayed in the docs.
""")
class IsbnValidationSpec extends Specification  {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            [
            'spec.name': IsbnValidationSpec.class.simpleName,
            ], Environment.TEST)

    def "IsbnValidatorFunction and IsbnValidatorClient are loaded"() {
        when:
        context.getBean(IsbnValidatorFunction)

        then:
        noExceptionThrown()

        when:
        context.getBean(IsbnValidatorClient)

        then:
        noExceptionThrown()

    }
}
