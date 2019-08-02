package io.micronaut.http.server.multipart;

import io.micronaut.http.multipart.CompletedPart;
import org.reactivestreams.Publisher;

public interface MultipartBody extends Publisher<CompletedPart> {

}
