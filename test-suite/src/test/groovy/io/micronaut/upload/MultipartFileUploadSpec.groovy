package io.micronaut.upload

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.http.server.netty.multipart.CompletedFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong

class MultipartFileUploadSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
    @Shared @AutoCleanup HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

    static final File uploadDir = File.createTempDir()

    void cleanup() {
        uploadDir.listFiles()*.delete()
    }

    void cleanupSpec() {
        uploadDir.delete()
    }

    void "test multipart file request byte[]"() {
        given:
        File file = new File(uploadDir, "data.txt")
        file.text = "test file"
        file.createNewFile()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/upload", MultipartBody.builder().addPart("data", file.name, MediaType.TEXT_PLAIN_TYPE, file))
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()

        then:
        body == "Uploaded 9 bytes"
    }

    void "test multipart file request byte[] without content type"() {
        given:
        File file = new File(uploadDir, "data.txt")
        file.text = "test file"
        file.createNewFile()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/upload", MultipartBody.builder().addPart("data", file.name, file.bytes))
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()

        then:
        body == "Uploaded 9 bytes"
    }

    void "test upload FileUpload object via CompletedFileUpload"() {
        given:
        File file = new File(uploadDir, "walkingthehimalayas.txt")
        file.text = "test file"
        file.createNewFile()

        when:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", file)
                .addPart("title", "Walking The Himalayas")
                .build()

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/completeFileUpload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()
        def newFile = new File(uploadDir, "Walking The Himalayas.txt")

        then:
        body == "Uploaded 9 bytes"
        newFile.exists()
        newFile.text == file.text

    }

    void "test upload InputStream"() {
        given:
        File file = new File(uploadDir, "walkingthehimalayas.txt")
        file.createNewFile()
        file << "test file input stream"

        when:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", file.name, MediaType.TEXT_PLAIN_TYPE, file.newInputStream(), file.length())
                .addPart("title", "Walking The Himalayas")
                .build()

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/completeFileUpload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()
        def newFile = new File(uploadDir, "Walking The Himalayas.txt")

        then:
        body == "Uploaded ${file.length()} bytes"
        newFile.exists()
        newFile.text == file.text

    }


    void "test upload InputStream without ContentType"() {
        given:
        File file = new File(uploadDir, "walkingthehimalayas.txt")
        file.createNewFile()
        file << "test file input stream"

        when:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", file.name, file.newInputStream(), file.length())
                .addPart("title", "Walking The Himalayas")
                .build()

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/completeFileUpload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()
        def newFile = new File(uploadDir, "Walking The Himalayas.txt")

        then:
        body == "Uploaded ${file.length()} bytes"
        newFile.exists()
        newFile.text == file.text

    }

    void "test upload FileUpload object via transferTo"() {
        given:
        File file = new File(uploadDir, "walkingthehimalayas.txt")
        file.text = "test file"
        file.createNewFile()

        when:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", file)
                .addPart("title", "Walking The Himalayas")
                .build()

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/streamFileUpload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()
        def newFile = new File(uploadDir, "Walking The Himalayas.txt")

        then:
        body == "Uploaded"
        newFile.exists()
        newFile.text == file.text

    }

    void "test upload big FileUpload object via transferTo"() {
        given:
        def val = 'Big '+ 'xxxx' * 500
        def data = '{"title":"'+val+'"}'
        File datafile = new File(uploadDir, "data.json")
        datafile.createNewFile()
        datafile.text = data
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data",  datafile.name, MediaType.APPLICATION_JSON_TYPE, datafile)
                .addPart("title", "bar")
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/streamBigFileUpload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))

        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()
        def file = new File(uploadDir, "bar.json")

        then:
        response.code() == HttpStatus.OK.code
        body == "Uploaded"
        file.exists()
        file.text == data
    }

    void "test non-blocking upload with publisher receiving bytes"() {
        given:
        def data = 'some data ' * 500
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.TEXT_PLAIN_TYPE, data.bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/receivePublisher", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        def response = flowable.blockingFirst()
        def result = response.getBody().get()

        then:
        response.code() == HttpStatus.OK.code
        result.length() == data.length()
        result == data

    }

    @Ignore
    void "test non-blocking upload with publisher receiving two objects"() {
        given:
        def data = '{"title":"Test"}'
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .addPart("title", "bar")
                .build()


        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/receiveTwoFlowParts", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        def response = flowable.blockingFirst()
        def result = response.getBody().get()

        then:
        response.code() == HttpStatus.OK.code
        result.length() == data.length()
        result == data

    }

    void "test non-blocking upload with publisher receiving converted JSON"() {
        given:
        def data = '{"title":"Test"}'
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .addPart("title", "bar")
                .build()


        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/recieveFlowData", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.APPLICATION_JSON_TYPE),
                String
        ))
        def response = flowable.blockingFirst()
        def body = response.getBody().get()

        then:
        response.code() == HttpStatus.OK.code
        body == 'Data{title=\'Test\'}'

        when:"a large document with partial data is uploaded"
        def val = 'Big '+ 'xxxx' * 200
        data = '{"title":"'+val+'"}'
        requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .addPart("title", "bar")
                .build()
        flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/recieveFlowData", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        response = flowable.blockingFirst()
        body = response.getBody().get()

        then:
        response.code() == HttpStatus.OK.code
        body.contains(val) // TODO: optimize this to use Jackson non-blocking and JsonNode


    }


    @Controller('/multipart')
    static class MultipartController {


        @Post(uri = '/upload', consumes = MediaType.MULTIPART_FORM_DATA)
        HttpResponse<String> upload(byte[] data) {
            return HttpResponse.ok("Uploaded " + data.length + " bytes")
        }

        @Post(consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<HttpResponse> completeFileUpload(CompletedFileUpload data, String title) {
            File newFile = new File(uploadDir, title + ".txt")
            newFile.createNewFile()
            newFile.append(data.getInputStream())
            return Flowable.just(HttpResponse.ok("Uploaded ${newFile.length()} bytes"))
        }

        @Post(consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<HttpResponse> streamFileUpload(StreamingFileUpload data, String title) {
            return Flowable.fromPublisher(data.transferTo(new File(uploadDir, title + ".txt"))).map ({success->
                success ? HttpResponse.ok("Uploaded") :
                HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Something bad happened")
            })
        }

        @Post(consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<HttpResponse> streamBigFileUpload(StreamingFileUpload data, String title) {
            return Flowable.fromPublisher(data.transferTo(new File(uploadDir, title + ".json"))).map ({success->
                success ? HttpResponse.ok("Uploaded") :
                        HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Something bad happened")
            })
        }

        @Post(consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<HttpResponse> receivePublisher(@Part Flowable<byte[]> data) {
            StringBuilder builder = new StringBuilder()
            AtomicLong length = new AtomicLong(0)
            PublishSubject<HttpResponse> subject = PublishSubject.create()
            data
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                    new Subscriber<byte[]>() {
                        Subscription subscription
                        @Override
                        void onSubscribe(Subscription s) {
                            s.request(1)
                            this.subscription = s
                        }

                        @Override
                        void onNext(byte[] bytes) {
                            builder.append(new String(bytes))
                            length.addAndGet(bytes.length)
                            subscription.request(1)
                        }

                        @Override
                        void onError(Throwable t) {
                            subject.onError(t)
                        }

                        @Override
                        void onComplete() {
                            subject.onNext(HttpResponse.ok(builder.toString()))
                            subject.onComplete()
                        }
                    })
            return subject.toFlowable(BackpressureStrategy.ERROR)
        }

        @Post(consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<HttpResponse> recieveFlowData(@Part Flowable<Data> data) {
            return data.flatMap({ Flowable.just(it)}, {left, right -> left == right ? left.toString() : left.toString() + " " + right.toString()}).map({result-> HttpResponse.ok(result)})
        }

        @Post(consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<HttpResponse> receiveTwoFlowParts(@Part Flowable<Data> dataPublisher, @Part Flowable<String> titlePublisher) {
            return titlePublisher.zipWith(dataPublisher, {title, data -> HttpResponse.ok( title + ": " + data.toString() )})
        }

        static class Data {
            String title

            @Override
            String toString() {
                return "Data{" +
                        "title='" + title + '\'' +
                        '}'
            }
        }

    }
}
