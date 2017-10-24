/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.http.server.netty.upload;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.MediaType;
import org.particleframework.http.annotation.Body;
import org.particleframework.stereotype.Controller;
import org.particleframework.web.router.annotation.Post;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Controller
public class UploadController {

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public String receiveJson(Data data, String title) {
        return title + ": " + data.toString();
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public String receivePlain(String data, String title) {
        return title + ": " + data;
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public Publisher<HttpResponse> receivePublisher(@Body Flowable<byte[]> data/*, Flowable<String> title*/) {
        Argument.of(byte[].class);
        StringBuilder builder = new StringBuilder();
        return data.subscribeOn(Schedulers.io())
                .concatMap(bytes -> Flowable.just(builder.append(new String(bytes))))
                .map(result -> builder.toString())
                .map(HttpResponse::ok);
    }

    public static class Data {
        String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return "Data{" +
                    "title='" + title + '\'' +
                    '}';
        }
    }
}



