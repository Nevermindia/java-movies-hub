package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.api.ErrorResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class BaseHttpHandler implements HttpHandler {

    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected final Gson gson = new Gson();

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, response.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(response);
        }
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(204, -1);
    }

    protected void sendMethodNotAllowed(HttpExchange ex, String method) throws IOException {
        ex.getResponseHeaders().set("Allow", "GET, POST, DELETE");

        String message = "Метод " + method
                + " не поддерживается. Допустимые методы: GET, POST, DELETE";

        ErrorResponse errorResponse = new ErrorResponse(
                "Метод не поддерживается",
                List.of(message)
        );

        sendJson(ex, 405, gson.toJson(errorResponse));
    }
}