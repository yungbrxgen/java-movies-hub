package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static MoviesServer moviesServer;
    private static MoviesStore store;
    private static final Gson gson = new Gson();
    private static final String BASE_URI = "http://localhost:8080/movies";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String JSON_TYPE = "application/json";
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @BeforeAll
    public static void beforeAll() throws IOException {
        store = new MoviesStore();
        moviesServer = new MoviesServer(store, 8080);
        moviesServer.start();
    }

    @BeforeEach
    public void beforeEach() {
        moviesServer.getStore().clear();
    }

    @AfterAll
    public static void afterAll() {
        moviesServer.stop();
    }

    @Test
    public void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue(CONTENT_TYPE_HEADER).orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    public void shouldAddMovieWhenDataIsCorrect() throws IOException, InterruptedException {
        Movie movie = new Movie("Бойцовский клуб", 1999);
        String json = gson.toJson(movie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI))
                .header(CONTENT_TYPE_HEADER, JSON_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode()); // Ожидаем 201 Created
        Movie createdMovie = gson.fromJson(response.body(), Movie.class);
        assertNotNull(createdMovie.getId(), "Сервер должен присвоить ID");
        assertEquals("Бойцовский клуб", createdMovie.getTitle());
        assertEquals(1999, createdMovie.getYear());
    }

    @Test
    public void shouldReturn404WhenMovieDoesNotExist() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI + "/9999"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Фильм не найден"), "Тело ответа должно содержать сообщение 'Фильм не найден'");
    }

    @Test
    public void shouldDeleteExistingMovieApi() throws IOException, InterruptedException {
        Movie movieToAdd = new Movie("Матрица", 1999);
        String json = gson.toJson(movieToAdd);

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI))
                .header(CONTENT_TYPE_HEADER, JSON_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, postResponse.statusCode());
        Movie createdMovie = gson.fromJson(postResponse.body(), Movie.class);
        assertNotNull(createdMovie.getId());

        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI + "/" + createdMovie.getId()))
                .DELETE()
                .build();

        HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(204, deleteResponse.statusCode());

        assertNull(moviesServer.getStore().getById(createdMovie.getId()), "Фильм должен быть удален из хранилища");


        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI + "/" + createdMovie.getId()))
                .GET()
                .build();
        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, getResponse.statusCode());
    }

    @Test
    public void addAndCountMovieStore() {
        store.add(new Movie("Пираты Карибского моря", 2006));
        assertEquals(1, store.getAll().size());
    }

    @Test
    public void getByIdMovieStore() {
        Movie addedMovie = store.add(new Movie("Пираты Карибского моря", 2006));
        Movie expectedMovie = new Movie(addedMovie.getId(), "Пираты Карибского моря", 2006);

        assertEquals(expectedMovie, store.getById(addedMovie.getId()));
    }

    @Test
    public void getByDoesNotExistIdMovieStore() {
        assertNull(store.getById(999));
    }

    @Test
    public void getByYearMovieStore() {
        store.add(new Movie("Пираты Карибского моря", 2006));
        store.add(new Movie("Гарри Поттер", 2006));
        store.add(new Movie("Начало", 2010));

        List<Movie> movies2006 = store.getByYear(2006);
        assertEquals(2, movies2006.size());
        assertTrue(movies2006.stream().allMatch(m -> m.getYear() == 2006));
    }

    @Test
    public void deleteMovieStore() { // Переименовал
        Movie movie1 = new Movie("Гарри Поттер", 2006);
        Movie addedMovie = store.add(movie1);
        int id = addedMovie.getId();

        boolean isDeleted = store.delete(id);

        assertTrue(isDeleted);
        assertNull(store.getById(id));
        assertEquals(0, store.getAll().size());
    }

    @Test
    public void deleteDoesNotExistIdMovieStore() {
        boolean isDeleted = store.delete(999);
        assertFalse(isDeleted);
    }

    @Test
    public void shouldBeReturnEmptyListIfNoMoviesForYearStore() {
        store.add(new Movie("Матрица", 1999));

        List<Movie> result = store.getByYear(2025);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void shouldBeClearMoviesStore() {
        store.add(new Movie("Матрица", 1999));
        store.add(new Movie("Буратино", 2025));

        assertEquals(2, store.getAll().size());

        store.clear();
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    public void shouldReturn422WhenPostMovieWithEmptyTitle() throws IOException, InterruptedException {
        Movie movie = new Movie("", 1999);
        String json = gson.toJson(movie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI))
                .header(CONTENT_TYPE_HEADER, JSON_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("название не должно быть пустым"));
    }

    @Test
    public void shouldReturn422WhenPostMovieWithTooLongTitle() throws IOException, InterruptedException {
        String longTitle = "a".repeat(101);
        Movie movie = new Movie(longTitle, 1999);
        String json = gson.toJson(movie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI))
                .header(CONTENT_TYPE_HEADER, JSON_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("название слишком длинное"));
    }

    @Test
    public void shouldReturn422WhenPostMovieWithInvalidYear() throws IOException, InterruptedException {
        Movie movieTooOld = new Movie("Фильм", 1800);
        Movie movieTooNew = new Movie("Фильм", java.time.Year.now().getValue() + 2);

        // Тест для слишком старого фильма
        HttpRequest requestOld = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI))
                .header(CONTENT_TYPE_HEADER, JSON_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movieTooOld)))
                .build();
        HttpResponse<String> responseOld = client.send(requestOld, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, responseOld.statusCode());
        assertTrue(responseOld.body().contains("год должен быть между 1888"));

        HttpRequest requestNew = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI))
                .header(CONTENT_TYPE_HEADER, JSON_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movieTooNew)))
                .build();
        HttpResponse<String> responseNew = client.send(requestNew, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, responseNew.statusCode());
        assertTrue(responseNew.body().contains("год должен быть между 1888"));
    }

    @Test
    public void shouldReturn415WhenPostMovieWithIncorrectContentType() throws IOException, InterruptedException {
        Movie movie = new Movie("Фильм", 2000);
        String json = gson.toJson(movie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI))
                .header(CONTENT_TYPE_HEADER, "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(415, response.statusCode());
        assertTrue(response.body().contains("Неподдерживаемый Media Type"));
    }

    @Test
    public void shouldReturn400WhenGetMovieWithNonNumericId() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI + "/abc"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Некорректный ID"));
    }

    @Test
    public void shouldReturn400WhenDeleteMovieWithNonNumericId() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI + "/abc"))
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Идентификатор фильма должен быть числом"));
    }

    @Test
    public void shouldReturnMoviesFilteredByYear() throws IOException, InterruptedException {
        addMovieViaApi(new Movie("The Matrix", 1999));
        addMovieViaApi(new Movie("Fight Club", 1999));
        addMovieViaApi(new Movie("Inception", 2010));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI + "?year=1999"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        List<Movie> movies = gson.fromJson(response.body(), new ListOfMoviesTypeToken().getType());
        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(m -> m.getYear() == 1999));
        assertTrue(movies.stream().anyMatch(m -> m.getTitle().equals("The Matrix")));
        assertTrue(movies.stream().anyMatch(m -> m.getTitle().equals("Fight Club")));
    }

    @Test
    public void shouldReturnEmptyListForNonExistentYear() throws IOException, InterruptedException {
        addMovieViaApi(new Movie("The Matrix", 1999));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI + "?year=2025"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("[]", response.body(), "Должен вернуться пустой массив для несуществующего года");
    }

    @Test
    public void shouldReturn400WhenYearParameterIsNotNumeric() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI + "?year=abc"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Идентификатор фильма должен быть числом"));
    }

    private Movie addMovieViaApi(Movie movie) throws IOException, InterruptedException {
        String json = gson.toJson(movie);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI))
                .header(CONTENT_TYPE_HEADER, JSON_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode(), "Вспомогательный метод: Ошибка при добавлении фильма");
        return gson.fromJson(response.body(), Movie.class);
    }

    @Test
    public void shouldReturn405WhenMethodNotSupported() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI))
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());


        assertEquals(405, response.statusCode(), "Сервер должен возвращать 405 статус для неподдерживаемого метода PUT");
        assertTrue(response.body().contains("Метод не поддерживается"), "Тело ответа должно содержать описание ошибки");
    }

    @Test
    public void shouldReturn405WhenMethodNotSupportedOnIdPath() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URI + "/1"))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode(), "Сервер должен возвращать 405 статус для метода PATCH на пути /movies/{id}");
    }
}