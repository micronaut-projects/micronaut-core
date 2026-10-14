import java

from typing import Annotated

from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property
from micronaut.inject.qualifiers import Qualifiers
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="labelled-channels.sms.sender", value="Fred")
@Property(name="labelled-channels.email.sender", value="Bob")
@MicronautTest
class LabelInterceptorTest:
    context: Annotated[ApplicationContext, Inject] = None

    @Test
    def test_each_target_is_labelled_with_its_qualifier(self) -> None:
        Channel = java.type("micronaut.docs.aop.lifecycle.pertarget.labelled.Channel")
        # tag::test[]
        sms = self.context.getBean(Channel, Qualifiers.byName("sms"))
        email = self.context.getBean(Channel, Qualifiers.byName("email"))

        assert "[sms] Hello from Fred" == str(sms.send("Hello"))
        assert "[email] Hello from Bob" == str(email.send("Hello"))
        # end::test[]
