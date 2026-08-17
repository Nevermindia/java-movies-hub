package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore moviesStore;
    private final static int currentYear = LocalDate.now().getYear();

    public MoviesHandler(MoviesStore moviesStore) {
        this.moviesStore = moviesStore;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String[] split = path.split("/");

        switch (method) {
            case "GET" -> handleGet(ex, path, split);
            case "POST" -> handlePost(ex);
            case "DELETE" -> handleDelete(ex, path, split);
            default -> sendMethodNotAllowed(ex, method);
        }
    }

    private void handleGet(HttpExchange ex, String path, String[] split) throws IOException {
        if (path.equals("/movies") && split.length == 2) {
            String query = ex.getRequestURI().getQuery();
            if (query != null && query.contains("year=")) {
                handleGetByYear(ex);
            } else {
                String json = gson.toJson(moviesStore.getMovies().values());
                sendJson(ex, 200, json);
            }
        } else if (path.startsWith("/movies/") && split.length == 3) {
            handleGetById(ex, split[2]);
        } else {
            sendError(ex, 404, "Не найдено", "Путь не найден");
        }
    }

    private void handleGetById(HttpExchange ex, String id) throws IOException {
        Optional<Integer> optionalId = parseId(id);
        if (optionalId.isEmpty()) {
            sendError(ex, 400, "Некорректный ID", "Id должен быть числом");
            return;
        }

        int movieId = optionalId.get();
        Optional<Movie> movieOptional = moviesStore.findMovieById(movieId);
        if (movieOptional.isPresent()) {
            String json = gson.toJson(movieOptional.get());
            sendJson(ex, 200, json);
        } else {
            sendError(ex, 404, "Не найден", "Фильма с таким id нет в списке: " + id);
        }
    }

    private void handleGetByYear(HttpExchange ex) throws IOException {
        String year = extractYearParam(ex.getRequestURI().getQuery());
        if (year == null || !year.matches("\\d+") || year.length() != 4) {
            sendError(ex, 400, "Некорректный параметр запроса — 'year'",
                    "Год должен быть числом из 4х цифр");
        } else if (Integer.parseInt(year) < 1888 || Integer.parseInt(year) > currentYear + 1) {
            sendError(ex, 422, "Ошибка валидации", "Год должен быть между 1888 и " + (currentYear + 1));
        } else {
            String json = gson.toJson(moviesStore.findMoviesByYear(Integer.parseInt(year)));
            sendJson(ex, 200, json);
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        try {
            String requestBody = new String(ex.getRequestBody().readAllBytes(), UTF_8);
            if (requestBody.trim().isEmpty()) {
                sendError(ex, 400, "Ошибка формата запроса", "Тело запроса не должно быть пустым");
                return;
            }

            Movie movie = gson.fromJson(requestBody, Movie.class);
            if (movie == null) {
                sendError(ex, 400, "Ошибка формата запроса", "Некорректный JSON");
                return;
            }

            List<String> validationErrors = validateMovie(movie);
            List<String> contentTypeErrors = validateContentType(ex);

            if (!contentTypeErrors.isEmpty()) {
                String json = gson.toJson(new ErrorResponse("Ошибка формата запроса", contentTypeErrors));
                sendJson(ex, 415, json);
            } else if (!validationErrors.isEmpty()) {
                String json = gson.toJson(new ErrorResponse("Ошибка валидации", validationErrors));
                sendJson(ex, 422, json);
            } else {
                moviesStore.addMovie(movie);
                String json = gson.toJson(movie);
                sendJson(ex, 201, json);
            }
        } catch (Exception e) {
            sendError(ex, 400, "Ошибка формата запроса", "Некорректный JSON: " + e.getMessage());
        }
    }

    private void handleDelete(HttpExchange ex, String path, String[] split) throws IOException {
        if (!path.startsWith("/movies/") || split.length != 3) {
            sendError(ex, 404, "Не найдено", "Путь не найден");
            return;
        }

        String id = split[2];
        Optional<Integer> optionalId = parseId(id);
        if (optionalId.isEmpty()) {
            sendError(ex, 400, "Некорректный ID", "Id должен быть числом");
            return;
        }

        int movieId = optionalId.get();
        Optional<Movie> movieOptional = moviesStore.findMovieById(movieId);
        if (movieOptional.isEmpty()) {
            sendError(ex, 404, "Не найден", "Фильма с таким id нет в списке: " + id);
            return;
        }

        moviesStore.deleteMovie(movieId);
        sendNoContent(ex);
    }

    private Optional<Integer> parseId(String id) {
        if (id == null || !id.matches("\\d+")) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(id));
    }

    private String extractYearParam(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("year=")) {
                return param.substring(5);
            }
        }
        return null;
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();
        if (movie.getTitle() == null || movie.getTitle().isEmpty()) {
            errors.add("Название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("Название не должно длинее 100 символов");
        }

        if (movie.getYear() < 1888 || movie.getYear() > currentYear + 1) {
            errors.add("Год должен быть между 1888 и " + (currentYear + 1));
        }
        return errors;
    }

    private List<String> validateContentType(HttpExchange ex) {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            return List.of("Неправильное значение заголовка Content-Type");
        }
        return Collections.emptyList();
    }

    private void sendError(HttpExchange ex, int status, String title, String detail) throws IOException {
        String json = gson.toJson(new ErrorResponse(title, List.of(detail)));
        sendJson(ex, status, json);
    }
}