package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new HashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public Movie add(Movie movie) {
        int id = nextId.getAndIncrement();
        movie.setId(id);
        movies.put(id, movie);
        return movie;
    }

    public Collection<Movie> getAll() {
        return new ArrayList<>(movies.values());
    }

    public Movie getById(int id) {
        return movies.get(id);
    }

    public boolean delete(int id) {
        return movies.remove(id) != null;
    }

    public List<Movie> getByYear(int year) {
        return movies.values().stream()
                .filter(movie -> movie.getYear() == year)
                .collect(Collectors.toList());
    }

    public void clear() {
        movies.clear();
        nextId.set(1);
    }
}