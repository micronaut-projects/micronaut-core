package io.micronaut.http.client.rxjava2;



public class Movie {
    private String imdbId;
    private boolean isInCollection;

    public Movie(String imdbId, boolean isInCollection) {
        this.imdbId = imdbId;
        this.isInCollection = isInCollection;
    }

    public String getImdbId() {
        return imdbId;
    }

    public void setImdbId(String imdbId) {
        this.imdbId = imdbId;
    }

    public boolean isInCollection() {
        return isInCollection;
    }

}
