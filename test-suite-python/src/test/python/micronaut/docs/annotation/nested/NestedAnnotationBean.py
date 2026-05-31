import java

from jakarta.inject import Singleton


NestedAnnotation = java.type("micronaut.docs.annotation.nested.NestedAnnotation")
NestedMember = java.type("micronaut.docs.annotation.nested.NestedMember")


@Singleton
@NestedAnnotation(
    member=NestedMember(value="one"),
    members=[NestedMember(value="two")],
)
class NestedAnnotationBean:
    pass
