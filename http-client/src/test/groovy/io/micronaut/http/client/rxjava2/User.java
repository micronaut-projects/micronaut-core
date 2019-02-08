package io.micronaut.http.client.rxjava2;

import java.util.List;

public class User {
    private String userName;
    private List<Movie> movies;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public List<Movie> getMovies() {
        return movies;
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
    }

}
