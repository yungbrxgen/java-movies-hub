package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


public class MoviesHandler extends BaseHttpHandler implements HttpHandler {
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String[] pathParts = path.split("/");

        System.out.println("Процессинг: " + method + " " + path);

        try {
            switch (method) {
                case "GET":
                    handleGet(exchange, pathParts);
                    break;
                case "POST":
                    if (pathParts.length == 2 && pathParts[1].equals("movies")) {
                        handlePost(exchange);
                    } else {
                        sendText(exchange, "Некорректный путь для POST запроса", 400);
                    }
                    break;
                case "DELETE":
                    handleDelete(exchange, pathParts);
                    break;
                default:
                    sendText(exchange, "Метод не поддерживается", 405);
            }
        } catch (Exception e) {
            sendText(exchange, "Ошибка сервера", 500);
        }
    }

    private void handleGet(HttpExchange exchange, String[] pathParts) throws IOException {
        if (pathParts.length == 2 && pathParts[1].equals("movies")) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.startsWith("year=")) {
                try {
                    int year = Integer.parseInt(query.split("=")[1]);
                    sendJson(exchange, store.getByYear(year), 200);
                } catch (NumberFormatException e) {
                    sendText(exchange, "Идентификатор фильма должен быть числом", 400);
                }
            } else {
                sendJson(exchange, store.getAll(), 200);
            }
        } else if (pathParts.length == 3 && pathParts[1].equals("movies")) {
            try {
                int id = Integer.parseInt(pathParts[2]);
                Movie movie = store.getById(id);
                if (movie != null) {
                    sendJson(exchange, movie, 200);
                } else {
                    sendText(exchange, "Фильм не найден", 404);
                }
            } catch (NumberFormatException e) {
                sendText(exchange, "Некорректный ID", 400);
            }
        } else {
            sendText(exchange, "Путь не найден", 404);
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendText(exchange, "Неподдерживаемый Media Type", 415);
            return;
        }

        String body;
        try {
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                sendHasErrors(exchange, "Ошибка парсинга JSON", List.of("Тело запроса не должно быть пустым"));
                return;
            }
        } catch (IOException e) {
            sendHasErrors(exchange, "Ошибка чтения запроса", List.of("Не удалось прочтитать тело запроса: " + e.getMessage()));
            return;
        }
        Movie movie;
        try {
            movie = gson.fromJson(body, Movie.class);
            if (movie == null || movie.getTitle() == null || movie.getYear() == 0) {
                sendHasErrors(exchange, "Некорректный JSON", List.of("JSON не содержит полную информацию о фильме"));
                return;
            }
            if (movie.getId() != null) {
                sendHasErrors(exchange, "Некорректный запрос", List.of("ID фильма не должен указываться в запросе POST"));
                return;
            }
        } catch (com.google.gson.JsonSyntaxException e) {
            sendText(exchange, "Некорректный JSON", 400);
            return;
        }

        List<String> errors = new ArrayList<>();
        if (movie.getTitle().isBlank()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("название слишком длинное");
        }

        int currentYear = java.time.Year.now().getValue();
        if (movie.getYear() < 1888 || movie.getYear() > currentYear + 1) {
            errors.add("год должен быть между 1888 и " + (currentYear + 1));
        }

        if (!errors.isEmpty()) {
            sendHasErrors(exchange, "Ошибка валидации", errors);
            return;
        }

        Movie created = store.add(movie);
        sendJson(exchange, created, 201);
    }

    private void handleDelete(HttpExchange exchange, String[] pathParts) throws IOException {
        if (pathParts.length == 3 && pathParts[1].equals("movies")) {
            try {
                int id = Integer.parseInt(pathParts[2]);
                boolean deleted = store.delete(id);
                if (deleted) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                } else {
                    sendText(exchange, "Фильм не найден", 404);
                }
            } catch (NumberFormatException e) {
                sendText(exchange, "Идентификатор фильма должен быть числом", 400);
            }
        } else {
            sendHasErrors(exchange, "Некорректный путь", List.of("Метод DELETE поддерживается только для /movies/{id}"));
        }
    }
}