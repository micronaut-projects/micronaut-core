package io.micronaut.docs.context.events;
// tag::class[]
public class SampleEvent {
    private String message = "Something happened";

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
// end::class[]