package io.micronaut.docs.server.upload

// tag::class[]
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.http.multipart.CompletedPart
import io.micronaut.http.server.multipart.MultipartBody
import io.reactivex.Single
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

@Controller("/upload")
class WholeBodyUploadController {

    @Post(value = "/whole-body", consumes = [MediaType.MULTIPART_FORM_DATA]) // <1>
    fun uploadBytes(@Body body: MultipartBody): Single<String> { // <2>
        return Single.create { emitter ->
            body.subscribe(object : Subscriber<CompletedPart> {
                private var s: Subscription? = null

                override fun onSubscribe(s: Subscription) {
                    this.s = s
                    s.request(1)
                }

                override fun onNext(completedPart: CompletedPart) {
                    val partName = completedPart.name
                    if (completedPart is CompletedFileUpload) {
                        val originalFileName = completedPart.filename
                    }
                }

                override fun onError(t: Throwable) {
                    emitter.onError(t)
                }

                override fun onComplete() {
                    emitter.onSuccess("Uploaded")
                }
            })
        }
    }
}
// end::class[]
