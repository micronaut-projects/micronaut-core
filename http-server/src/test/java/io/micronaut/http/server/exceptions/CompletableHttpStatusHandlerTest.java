/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.micronaut.http.server.exceptions;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.simple.SimpleHttpRequest;
import java.util.concurrent.CompletionException;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author ratcashdev
 */
public class CompletableHttpStatusHandlerTest {

    ApplicationContext ctx;
    SimpleHttpRequest<String> request;

    @Before
    public void init() {
        request = new SimpleHttpRequest<>(HttpMethod.GET, "http://localhost", null);
        ctx = ApplicationContext.run();
    }

    @Test
    public void testWrappedHttpStatusExceptionHandling() {
        CompletableHttpStatusHandler cut = ctx.createBean(CompletableHttpStatusHandler.class);
        HttpResponse response = cut.handle(request,
                new CompletionException(new HttpStatusException(HttpStatus.CREATED, "test msg")));
        assertEquals(response.code(), HttpStatus.CREATED.getCode());
    }

    @Test
    public void testWrappedUnknownExceptionHandling() {
        CompletableHttpStatusHandler cut = ctx.createBean(CompletableHttpStatusHandler.class);
        HttpResponse response = cut.handle(request, new CompletionException(new RuntimeException("test msg")));
        assertEquals(response.code(), HttpStatus.INTERNAL_SERVER_ERROR.getCode());
    }

}
