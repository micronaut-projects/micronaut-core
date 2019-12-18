package io.micronaut.docs.streaming

// tag::imports[]
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import kotlinx.coroutines.flow.Flow

// end::imports[]

// tag::class[]
@Client("/streaming")
interface HeadlineFlowClient {
    // end::class[]

    // tag::streamingWithFlow[]
    @Get(value = "/headlinesWithFlow", processes = [MediaType.APPLICATION_JSON_STREAM]) // <1>
    fun streamFlow(): Flow<Headline> // <2>
    // tag::streamingWithFlow[]
}
