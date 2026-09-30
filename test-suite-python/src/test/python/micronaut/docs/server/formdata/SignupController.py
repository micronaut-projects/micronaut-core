from micronaut.context.annotation import Requires

# tag::imports[]
import java
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Post
from micronaut.http.form import FileUpload

CompletionStage = java.type("java.util.concurrent.CompletionStage")
# end::imports[]


@Requires(property="spec.name", value="ProfileControllerSpec")
# tag::class[]
@Controller("/signup")
class SignupController:

    @Post(consumes=MediaType.MULTIPART_FORM_DATA)
    def signup(self, name: str, avatar: FileUpload) -> CompletionStage:  # <1>
        return (avatar.bytes(64 * 1024)
                .thenApply(lambda content: f"Welcome {name}, your avatar has {len(content)} bytes"))
# end::class[]
