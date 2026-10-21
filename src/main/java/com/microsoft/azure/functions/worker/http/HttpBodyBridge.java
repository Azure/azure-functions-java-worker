package com.microsoft.azure.functions.worker.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.HttpResponseMessage.IOConsumer;
import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

/**
 * Bridges between the JDK {@link HttpExchange} surface and the Functions
 * worker's protobuf-based binding plumbing.
 *
 * <p>For HTTP-proxied invocations, the Functions host sends the request body
 * and headers over HTTP (to the worker's embedded proxy server) and the trigger
 * metadata over gRPC (in an {@link InvocationRequest} whose HTTP input has an
 * empty body). This class:</p>
 * <ul>
 *   <li>Reads the body off the {@code HttpExchange} and folds it into the
 *       {@code RpcHttp} payload that downstream binding code expects.</li>
 *   <li>Writes the {@code RpcHttp} response produced by the user function back
 *       to the {@code HttpExchange}.</li>
 * </ul>
 *
 * <p>Matches the host's {@code RpcHttpBodyOnly} contract: octet-stream and
 * multipart bodies are bytes; other content types, including JSON and an
 * absent content type, are strings. Empty bodies remain unset.</p>
 */
public final class HttpBodyBridge {
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final int READ_CHUNK = 8192;

    private HttpBodyBridge() {
    }

    /**
     * Returns a copy of {@code request} with HTTP metadata from {@code exchange},
     * preserving host-resolved route parameters. If no input parameter holds an
     * {@code RpcHttp} payload, {@code request} is returned unchanged.
     *
     * <p>When {@code bufferBody} is false, metadata is hydrated without touching
     * the live request stream.</p>
     */
    public static InvocationRequest enrichRequest(InvocationRequest request, HttpExchange exchange, boolean bufferBody)
            throws IOException {
        InvocationRequest.Builder builder = request.toBuilder();
        List<ParameterBinding> inputs = request.getInputDataList();
        boolean enriched = false;
        byte[] body = null;
        for (int i = 0; i < inputs.size(); i++) {
            ParameterBinding input = inputs.get(i);
            if (!input.getData().hasHttp()) {
                continue;
            }
            if (bufferBody && body == null) {
                body = readBody(exchange);
            }
            RpcHttp.Builder httpBuilder = input.getData().getHttp().toBuilder();
            httpBuilder.setMethod(exchange.getRequestMethod());
            httpBuilder.setUrl(requestUrl(exchange));
            httpBuilder.clearHeaders();
            exchange.getRequestHeaders().forEach((name, values) ->
                httpBuilder.putHeaders(name.toLowerCase(Locale.ROOT), String.join(",", values)));
            String originalHost = exchange.getRequestHeaders().getFirst("X-Forwarded-Host");
            if (originalHost != null) {
                httpBuilder.putHeaders("host", originalHost);
            }
            httpBuilder.clearQuery();
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null) {
                Map<String, String> queryValues = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                for (String entry : query.split("&")) {
                    if (entry.isEmpty()) {
                        continue;
                    }
                    String[] pair = entry.split("=", 2);
                    String key = URLDecoder.decode(pair[0], "UTF-8");
                    String value = pair.length == 2 ? URLDecoder.decode(pair[1], "UTF-8") : "";
                    queryValues.merge(key, value, (first, second) -> first + "," + second);
                }
                queryValues.forEach((key, value) -> {
                    if (!value.isEmpty()) {
                        httpBuilder.putQuery(key, value);
                    }
                });
            }
            if (bufferBody) {
                httpBuilder.setBody(buildBodyTypedData(body, contentType(exchange.getRequestHeaders())));
            }
            TypedData.Builder dataBuilder = input.getData().toBuilder().setHttp(httpBuilder);
            ParameterBinding patched = input.toBuilder().setData(dataBuilder).build();
            builder.setInputData(i, patched);
            enriched = true;
        }
        return enriched ? builder.build() : request;
    }

    private static String requestUrl(HttpExchange exchange) {
        Headers headers = exchange.getRequestHeaders();
        String host = headers.getFirst("X-Forwarded-Host");
        if (host == null) {
            host = headers.getFirst("Host");
        }
        String scheme = headers.getFirst("X-Forwarded-Proto");
        if (scheme == null) {
            scheme = "http";
        }
        return scheme + "://" + (host != null ? host : exchange.getLocalAddress().getHostString()
            + ":" + exchange.getLocalAddress().getPort()) + exchange.getRequestURI().toASCIIString();
    }

    /**
     * Writes an {@link RpcHttp} response (status + headers + body) to the
     * given {@link HttpExchange}. The exchange is left open for the caller
     * to close.
     */
    public static void writeRpcHttpResponse(HttpExchange exchange, RpcHttp response) throws IOException {
        int status = parseStatus(response.getStatusCode());
        applyResponseHeaders(exchange, response);
        if (response.getBody().getDataCase() != TypedData.DataCase.BYTES
                && response.getBody().getDataCase() != TypedData.DataCase.DATA_NOT_SET) {
            String type = exchange.getResponseHeaders().getFirst(CONTENT_TYPE_HEADER);
            if (type == null) {
                type = "text/plain";
            }
            if (!type.toLowerCase(Locale.ROOT).contains("charset=")) {
                type += "; charset=utf-8";
            }
            exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, type);
        }
        byte[] bodyBytes = extractBodyBytes(response.getBody(), response.getHeadersMap());
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod()) && status != 204 && status != 304) {
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(bodyBytes.length));
        }
        if (!allowsResponseBody(exchange, status) || bodyBytes.length == 0) {
            // -1 == no response body; the response body stream does not need to be opened.
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, bodyBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bodyBytes);
            }
        }
    }

    /**
     * Streams the body of an HTTP response from an {@link InputStream} directly
     * to the {@link HttpExchange} response stream using chunked transfer
     * encoding (when the upstream client supports it). The {@code envelope}
     * supplies status and headers; its body, if any, is ignored.
     *
     * <p>The supplied {@code body} stream is closed by this method regardless
     * of outcome. The exchange is left open for the caller to close.</p>
     */
    public static void writeStreamingResponse(HttpExchange exchange, RpcHttp envelope, InputStream body)
            throws IOException {
        try (InputStream in = body) {
            int status = parseStatus(envelope.getStatusCode());
            applyResponseHeaders(exchange, envelope);
            if (!allowsResponseBody(exchange, status)) {
                exchange.sendResponseHeaders(status, -1);
                return;
            }
            // A zero length selects chunked encoding without buffering the body.
            exchange.sendResponseHeaders(status, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] chunk = new byte[READ_CHUNK];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    os.write(chunk, 0, n);
                    os.flush();
                }
            }
        }
    }

    /**
     * Streams the body of an HTTP response by invoking the {@code writer}
     * callback with the {@link HttpExchange} response stream. The {@code envelope}
     * supplies status and headers; its body, if any, is ignored.
     *
     * <p>The response stream is opened (and chunked encoding selected) before
     * {@code writer} is invoked; it is flushed and closed when {@code writer}
     * returns. Any {@link IOException} thrown by {@code writer} propagates to
     * the caller. The exchange is left open for the caller to close.</p>
     */
    public static void writeStreamingResponse(HttpExchange exchange, RpcHttp envelope, IOConsumer<OutputStream> writer)
            throws IOException {
        int status = parseStatus(envelope.getStatusCode());
        applyResponseHeaders(exchange, envelope);
        if (!allowsResponseBody(exchange, status)) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, 0);
        try (OutputStream os = exchange.getResponseBody()) {
            writer.accept(os);
            os.flush();
        }
    }

    private static void applyResponseHeaders(HttpExchange exchange, RpcHttp envelope) {
        for (Map.Entry<String, String> header : envelope.getHeadersMap().entrySet()) {
            // HttpServer owns transport framing, particularly for unknown-length streams.
            if (!header.getKey().equalsIgnoreCase("Content-Length")
                    && !header.getKey().equalsIgnoreCase("Transfer-Encoding")) {
                exchange.getResponseHeaders().set(header.getKey(), header.getValue());
            }
        }
    }

    private static boolean allowsResponseBody(HttpExchange exchange, int status) {
        return !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())
            && status >= 200 && status != 204 && status != 205 && status != 304;
    }

    /**
     * Writes a plain text error response to the exchange. Used by the proxy
     * handler when it cannot wire up an invocation (missing header, lost
     * coordinator, etc.).
     */
    public static void writeErrorResponse(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = (message == null ? "" : message).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    static TypedData buildBodyTypedData(byte[] bytes, String contentType) {
        if (bytes.length == 0) {
            return TypedData.getDefaultInstance();
        }
        if (contentType != null) {
            String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("application/octet-stream") || normalized.contains("multipart/")) {
                return TypedData.newBuilder()
                    .setBytes(ByteString.copyFrom(bytes))
                    .build();
            }
        }
        return TypedData.newBuilder()
            .setString(new String(bytes, charsetFromContentType(contentType)))
            .build();
    }

    private static byte[] readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[READ_CHUNK];
            int n;
            while ((n = in.read(chunk)) != -1) {
                out.write(chunk, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String contentType(Headers headers) {
        return headers.getFirst(CONTENT_TYPE_HEADER);
    }

    private static Charset charsetFromContentType(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        int idx = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
        if (idx < 0) {
            return StandardCharsets.UTF_8;
        }
        String charset = contentType.substring(idx + "charset=".length()).trim();
        int semi = charset.indexOf(';');
        if (semi >= 0) {
            charset = charset.substring(0, semi).trim();
        }
        // strip enclosing quotes
        if (charset.length() >= 2
                && charset.charAt(0) == '"'
                && charset.charAt(charset.length() - 1) == '"') {
            charset = charset.substring(1, charset.length() - 1);
        }
        try {
            return Charset.forName(charset);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
            WorkerLogManager.getSystemLogger().log(Level.WARNING,
                "Unsupported HTTP body charset; using UTF-8", ex);
            return StandardCharsets.UTF_8;
        }
    }

    private static int parseStatus(String statusCode) {
        if (statusCode == null || statusCode.isEmpty()) {
            return 200;
        }
        int status = Integer.parseInt(statusCode);
        if (status < 200 || status > 599) {
            throw new IllegalArgumentException("Invalid final HTTP response status: " + statusCode);
        }
        return status;
    }

    private static byte[] extractBodyBytes(TypedData body, Map<String, String> headers) {
        if (body == null) {
            return new byte[0];
        }
        switch (body.getDataCase()) {
            case BYTES:
                return body.getBytes().toByteArray();
            case STRING:
                return body.getString().getBytes(charsetFromContentType(headerLookup(headers, "Content-Type")));
            case JSON:
                return ("null".equals(body.getJson().trim()) ? "" : body.getJson())
                    .getBytes(charsetFromContentType(headerLookup(headers, "Content-Type")));
            case INT:
                return Long.toString(body.getInt()).getBytes(StandardCharsets.UTF_8);
            case DOUBLE:
                return Double.toString(body.getDouble()).getBytes(StandardCharsets.UTF_8);
            case DATA_NOT_SET:
                return new byte[0];
            default:
                throw new IllegalArgumentException("Unsupported HTTP response body: " + body.getDataCase());
        }
    }

    private static String headerLookup(Map<String, String> headers, String key) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
