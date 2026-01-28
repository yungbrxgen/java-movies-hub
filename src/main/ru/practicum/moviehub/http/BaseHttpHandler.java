package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    protected void sendJson(HttpExchange exchange, Object body, int code) throws IOException {
        String json = gson.toJson(body);
        byte[] resp = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", CT_JSON);
        exchange.sendResponseHeaders(code, resp.length);
        exchange.getResponseBody().write(resp);
        exchange.close();
    }

    protected void sendText(HttpExchange exchange, String text, int code) throws IOException {
        ErrorResponse errorBody = new ErrorResponse(text);
        String json = gson.toJson(errorBody);
        byte[] jsonResp = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", CT_JSON);
        exchange.sendResponseHeaders(code, jsonResp.length);
        exchange.getResponseBody().write(jsonResp);
        exchange.close();
    }

    protected void sendNotFound(HttpExchange exchange, String message) throws IOException {
        ErrorResponse error = new ErrorResponse(message);
        sendJson(exchange, error, 404);
    }

    protected void sendHasErrors(HttpExchange exchange, String message, List<String> details) throws IOException {
        ErrorResponse error = new ErrorResponse(message, details);
        sendJson(exchange, error, 422);
    }
}