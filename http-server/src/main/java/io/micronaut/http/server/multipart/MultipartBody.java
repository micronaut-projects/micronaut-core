package io.micronaut.http.server.multipart;

import io.micronaut.http.multipart.Part;
import org.reactivestreams.Publisher;

public interface MultipartBody extends Publisher<Part> {

}
