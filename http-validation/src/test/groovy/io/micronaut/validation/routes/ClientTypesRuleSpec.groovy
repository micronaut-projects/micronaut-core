package io.micronaut.validation.routes

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class ClientTypesRuleSpec extends AbstractTypeElementSpec {

    void "test allowed return type"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import io.micronaut.http.client.annotation.Client;

@Client("/foo")
interface Foo {
    
    @Get
    String abc();
   
}

""")
        then:
        noExceptionThrown()
    }

    void "test disallowed return type"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.server.types.files.StreamedFile;

@Client("/foo")
interface Foo {
    
    @Get
    StreamedFile abc();    
   
}

""")
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Type [class io.micronaut.http.server.types.files.StreamedFile] and its subtypes " +
                "must not be used as return types in declarative client methods")
    }

    void "test disallowed return type subclass"() {
        when:
        buildTypeElement("""

package test;

import java.io.InputStream;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.server.types.files.StreamedFile;

class InheritedStreamedFile extends StreamedFile {
    public InheritedStreamedFile(InputStream inputStream, MediaType mediaType) {
        super(inputStream, mediaType);
    }
}

@Client("/foo")
interface Foo {
    @Get
    InheritedStreamedFile abc();
}

""")
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Type [class io.micronaut.http.server.types.files.StreamedFile] and its subtypes " +
                "must not be used as return types in declarative client methods")
    }

    void "test disallowed parameter type"() {
        when:
        buildTypeElement("""

package test;

import java.io.InputStream;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.multipart.MultipartBody;

@Client("/foo")
interface Foo {
    @Get
    String abc(MultipartBody file);
}

""")
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Type [interface io.micronaut.http.server.multipart.MultipartBody] and its subtypes must not be used " +
                "as client method parameters. Use [class io.micronaut.http.client.multipart.MultipartBody] instead")
    }
}
