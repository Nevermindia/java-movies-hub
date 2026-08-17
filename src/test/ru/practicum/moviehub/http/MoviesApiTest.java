package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static Gson gson = new Gson();
    private static MoviesStore moviesStore;
    private static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";
    private static final int CURRENT_YEAR = LocalDate.now().getYear();

    @BeforeAll
    static void beforeAll() {
        moviesStore = new MoviesStore();
        server = new MoviesServer(moviesStore, 8080);
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @BeforeEach
    void beforeEach() {
        moviesStore.clearMovieMap();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = sendGetRequest("/movies");

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertContentType(resp);
        assertJsonArray(resp.body());
    }

    @Test
    void getMovies_whenNotEmpty_returnsArray() throws Exception {
        moviesStore.addMovie(new Movie("Матрица", 1999));
        moviesStore.addMovie(new Movie("Титаник", 1997));
        moviesStore.addMovie(new Movie("Зеленая миля", 1999));

        HttpResponse<String> resp = sendGetRequest("/movies");

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertContentType(resp);
        assertJsonArray(resp.body());
        assertArraySize(resp.body(), 3);
    }

    @Test
    void postMovie_whenValid_shouldAddMovie() throws Exception {
        Movie newMovie = new Movie("Интерстеллар", 2014);
        String requestBody = gson.toJson(newMovie);

        HttpResponse<String> response = sendPostRequest(requestBody);

        assertEquals(201, response.statusCode());
        assertContentType(response);

        Movie createdMovie = gson.fromJson(response.body(), Movie.class);
        assertNotNull(createdMovie);
        assertTrue(createdMovie.getId() > 0);
        assertEquals("Интерстеллар", createdMovie.getTitle());
        assertEquals(2014, createdMovie.getYear());

        HttpResponse<String> getResponse = sendGetRequest("/movies");

        assertEquals(200, getResponse.statusCode());

        Movie[] movies = gson.fromJson(getResponse.body(), Movie[].class);
        assertEquals(1, movies.length);
        assertEquals(createdMovie.getId(), movies[0].getId());
        assertEquals("Интерстеллар", movies[0].getTitle());
        assertEquals(2014, movies[0].getYear());
    }

    @Test
    void postMovie_whenEmptyTitle_shouldReturnError() throws Exception {
        Movie invalidMovie = new Movie("", 2020);
        String requestBody = gson.toJson(invalidMovie);

        HttpResponse<String> response = sendPostRequest(requestBody);

        assertEquals(422, response.statusCode());
        assertContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().contains("Название не должно быть пустым"));
    }

    @Test
    void postMovie_whenTitleTooLong_shouldReturnError() throws Exception {
        String longTitle = "A".repeat(101);
        Movie invalidMovie = new Movie(longTitle, 2020);
        String requestBody = gson.toJson(invalidMovie);

        HttpResponse<String> response = sendPostRequest(requestBody);

        assertEquals(422, response.statusCode());
        assertContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().contains("Название не должно длинее 100 символов"));
    }

    @Test
    void postMovie_whenYearTooOld_shouldReturnError() throws Exception {
        Movie invalidMovie = new Movie("Старый фильм", 1887);
        String requestBody = gson.toJson(invalidMovie);

        HttpResponse<String> response = sendPostRequest(requestBody);

        assertEquals(422, response.statusCode());
        assertContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().get(0).contains("Год должен быть между 1888"));
    }

    @Test
    void postMovie_whenYearTooFar_shouldReturnError() throws Exception {
        Movie invalidMovie = new Movie("Футуристический фильм", CURRENT_YEAR + 2);
        String requestBody = gson.toJson(invalidMovie);

        HttpResponse<String> response = sendPostRequest(requestBody);

        assertEquals(422, response.statusCode());
        assertContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().get(0).contains("Год должен быть между 1888"));
    }

    @Test
    void postMovie_whenInvalidContentType_shouldReturnError() throws Exception {
        Movie validMovie = new Movie("Дюна", 2021);
        String requestBody = gson.toJson(validMovie);

        HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, response.statusCode());
        assertContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка формата запроса", error.getError());
        assertTrue(error.getDetails().contains("Неправильное значение заголовка Content-Type"));
    }

    @Test
    void postMovie_whenInvalidJson_shouldReturnError() throws Exception {
        String invalidJson = "{ invalid json }";

        HttpResponse<String> response = sendPostRequest(invalidJson);

        assertEquals(400, response.statusCode());
        assertContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка формата запроса", error.getError());
        assertTrue(error.getDetails().get(0).contains("Некорректный JSON"));
    }

    @Test
    void getMovieById_whenExists_shouldReturnMovie() throws Exception {
        Movie movie = new Movie("Матрица", 1999);
        moviesStore.addMovie(movie);
        int movieId = movie.getId();

        HttpResponse<String> response = sendGetRequest("/movies/" + movieId);

        assertEquals(200, response.statusCode());
        assertContentType(response);

        Movie foundMovie = gson.fromJson(response.body(), Movie.class);
        assertEquals(movieId, foundMovie.getId());
        assertEquals("Матрица", foundMovie.getTitle());
        assertEquals(1999, foundMovie.getYear());
    }

    @Test
    void getMovieById_whenNotFound_shouldReturnError() throws Exception {

        HttpResponse<String> response = sendGetRequest("/movies/999");

        assertEquals(404, response.statusCode());
        assertContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Не найден", error.getError());
        assertTrue(error.getDetails().get(0).contains("Фильма с таким id нет в списке"));
    }

    @Test
    void getMovieById_whenInvalidId_shouldReturnError() throws Exception {
        HttpResponse<String> response = sendGetRequest("/movies/abc");

        assertEquals(400, response.statusCode());
        assertContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Некорректный ID", error.getError());
        assertTrue(error.getDetails().contains("Id должен быть числом"));
    }

    @Test
    void deleteMovie_whenExists_shouldDeleteMovie() throws Exception {
        Movie newMovie = new Movie("Титаник", 1997);

        HttpResponse<String> createResponse =
                sendPostRequest(gson.toJson(newMovie));

        assertEquals(201, createResponse.statusCode());

        Movie createdMovie =
                gson.fromJson(createResponse.body(), Movie.class);

        int movieId = createdMovie.getId();

        HttpResponse<String> deleteResponse =
                sendDeleteRequest("/movies/" + movieId);

        assertEquals(204, deleteResponse.statusCode());

        HttpResponse<String> getResponse =
                sendGetRequest("/movies/" + movieId);

        assertEquals(404, getResponse.statusCode());
    }

    @Test
    void deleteMovie_whenNotFound_shouldReturnError() throws Exception {
        HttpResponse<String> response = sendDeleteRequest("/movies/999");

        assertEquals(404, response.statusCode());
        assertContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Не найден", error.getError());
        assertTrue(error.getDetails().get(0).contains("Фильма с таким id нет в списке"));
    }

    @Test
    void deleteMovie_whenInvalidId_shouldReturnError() throws Exception {
        HttpResponse<String> response = sendDeleteRequest("/movies/abc");

        assertEquals(400, response.statusCode());
        assertContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Некорректный ID", error.getError());
        assertTrue(error.getDetails().contains("Id должен быть числом"));
    }

    @Test
    void getMoviesByYear_whenMoviesExist_shouldReturnMovies() throws Exception {

        moviesStore.addMovie(new Movie("Матрица", 1999));
        moviesStore.addMovie(new Movie("Зеленая миля", 1999));
        moviesStore.addMovie(new Movie("Титаник", 1997));

        HttpResponse<String> response = sendGetRequest("/movies?year=1999");

        assertEquals(200, response.statusCode());
        assertContentType(response);

        Movie[] movies = gson.fromJson(response.body(), Movie[].class);
        assertEquals(2, movies.length);
        for (Movie movie : movies) {
            assertEquals(1999, movie.getYear());
        }
    }

    @Test
    void getMoviesByYear_whenNoMovies_shouldReturnEmptyArray() throws Exception {

        moviesStore.addMovie(new Movie("Титаник", 1997));

        HttpResponse<String> response = sendGetRequest("/movies?year=2020");

        assertEquals(200, response.statusCode());
        assertContentType(response);

        Movie[] movies = gson.fromJson(response.body(), Movie[].class);
        assertEquals(0, movies.length);
        assertTrue(response.body().trim().equals("[]") ||
                response.body().trim().startsWith("[") && response.body().trim().endsWith("]"));
    }

    @Test
    void getMoviesByYear_whenInvalidYear_shouldReturnError() throws Exception {

        HttpResponse<String> response = sendGetRequest("/movies?year=abc");

        assertEquals(400, response.statusCode());
        assertContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Некорректный параметр запроса — 'year'", error.getError());
        assertTrue(error.getDetails().contains("Год должен быть числом из 4х цифр"));
    }

    @Test
    void getMoviesByYear_whenYearTooShort_shouldReturnError() throws Exception {

        HttpResponse<String> response = sendGetRequest("/movies?year=99");

        assertEquals(400, response.statusCode());
        assertContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Некорректный параметр запроса — 'year'", error.getError());
        assertTrue(error.getDetails().contains("Год должен быть числом из 4х цифр"));
    }

    @Test
    void putMethod_shouldReturn405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, response.statusCode());

        String allowHeader = response.headers().firstValue("Allow").orElse("");
        assertTrue(allowHeader.contains("GET"));
        assertTrue(allowHeader.contains("POST"));
        assertTrue(allowHeader.contains("DELETE"));

        assertContentType(response);
        String body = response.body();
        assertNotNull(body);
        assertTrue(body.contains("Метод PUT не поддерживается") ||
                body.contains("Метод не поддерживается"));
    }

    @Test
    void patchMethod_shouldReturn405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, response.statusCode());
        assertTrue(response.headers().firstValue("Allow").orElse("").contains("GET"));
        assertTrue(response.headers().firstValue("Allow").orElse("").contains("POST"));
        assertTrue(response.headers().firstValue("Allow").orElse("").contains("DELETE"));
    }

    private void assertJsonArray(String body) {
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    private void assertArraySize(String body, int expectedSize) {
        Movie[] movies = gson.fromJson(body, Movie[].class);
        assertEquals(expectedSize, movies.length,
                "Массив должен содержать " + expectedSize + " фильмов");
    }

    private HttpResponse<String> sendPostRequest(String requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendGetRequest(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + path))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendDeleteRequest(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create(BASE + path))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertContentType(HttpResponse<String> response) {
        String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("");
        assertEquals(CONTENT_TYPE_JSON, contentType,
                "Content-Type должен содержать формат данных и кодировку");
    }

}