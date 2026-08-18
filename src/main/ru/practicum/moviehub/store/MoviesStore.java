package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;

public class MoviesStore {
    private final Map<Integer, Movie> movies;
    private int nextId = 1;

    public MoviesStore() {
        this.movies = new HashMap<>();
    }

    public Map<Integer, Movie> getMovies() {
        return Collections.unmodifiableMap(movies);
    }

    public void addMovie(Movie movie) {
        movie.setId(nextId);
        movies.put(nextId, movie);
        nextId++;
    }

    public Optional<Movie> findMovieById(int id) {
        return Optional.ofNullable(movies.get(id));
    }

    public List<Movie> findMoviesByYear(int year) {
        return movies.values().stream()
                .filter(m -> m.getYear().equals(year))
                .toList();
    }

    public void deleteMovie(int id) {
        movies.remove(id);
    }

    public void clearMovieMap() {
        movies.clear();
    }
}