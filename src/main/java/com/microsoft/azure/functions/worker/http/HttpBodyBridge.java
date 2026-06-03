package com.microsoft.azure.functions.worker.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;
import com.microsoft.azure.functions.rpc.messages.TypedData;
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
 * <p>Body classification mirrors the host's {@code PopulateBody} logic and the
 * existing worker behavior for in-process bodies: {@code application/json} →
 * {@code TypedData.json}; {@code text/*} and form-encoded → {@code TypedData.string};
 * everything else (including absent {@code Content-Type}) → {@code TypedData.bytes}.</p>
 */
public final class HttpBodyBridge {
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final int READ_CHUNK = 8192;

    private HttpBodyBridge() {
    }

    /**
     * Returns a copy of {@code request} with the body of its HTTP input replaced
     * by the body read from {@code exchange}. If no input parameter holds an
     * {@code RpcHttp} payload, {@code request} is returned unchanged.
     *
     * <p>The body is read eagerly into memory. Streaming support (InputStream
     * as a parameter type) is layered on in a later commit and bypasses this
     * eager read.</p>
     */
    public static InvocationRequest enrichRequestWithBody(InvocationRequest request, HttpExchange exchange)
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
            if (body == null) {
                body = readBody(exchange);
            }
            RpcHttp.Builder httpBuilder = input.getData().getHttp().toBuilder();
            httpBuilder.setBody(buildBodyTypedData(body, contentType(exchange.getRequestHeaders())));
            TypedData.Builder dataBuilder = input.getData().toBuilder().setHttp(httpBuilder);
            ParameterBinding patched = input.toBuilder().setData(dataBuilder).build();
            builder.setInputData(i, patched);
            enriched = true;
        }
        return enriched ? builder.build() : request;
    }

    /**
     * Writes an {@link RpcHttp} response (status + headers + body) to the
     * given {@link HttpExchange}. The exchange is left open for the caller
     * to close.
     */
    public static void writeRpcHttpResponse(HttpExchange exchange, RpcHttp response) throws IOException {
        int status = parseStatus(response.getStatusCode());
        for (Map.Entry<String, String> header : response.getHeadersMap().entrySet()) {
            exchange.getResponseHeaders().add(header.getKey(), header.getValue());
        }
        byte[] bodyBytes = extractBodyBytes(response.getBody(), response.getHeadersMap());
        if (bodyBytes.length == 0) {
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
     * Writes a plain text error response to the exchange. Used by the proxy
     * handler when it cannot wire up an invocation (missing header, lost
     * coordinator, etc.).
     */
    public static void writeErrorResponse(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = (message == null ? "" : message).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    static TypedData buildBodyTypedData(byte[] bytes, String contentType) {
        if (contentType != null) {
            String normalized = contentType.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("application/json")) {
                return TypedData.newBuilder()
                    .setJson(new String(bytes, charsetFromContentType(contentType)))
                    .build();
            }
            if (normalized.startsWith("text/")
                    || normalized.startsWith("application/x-www-form-urlencoded")
                    || normalized.startsWith("application/xml")
                    || normalized.startsWith("application/javascript")) {
                return TypedData.newBuilder()
                    .setString(new String(bytes, charsetFromContentType(contentType)))
                    .build();
            }
        }
        return TypedData.newBuilder()
            .setBytes(ByteString.copyFrom(bytes))
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
        } catch (RuntimeException ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private static int parseStatus(String statusCode) {
        if (statusCode == null || statusCode.isEmpty()) {
            return 200;
        }
        try {
            int status = Integer.parseInt(statusCode);
            if (status < 100 || status > 599) {
                return 500;
            }
            return status;
        } catch (NumberFormatException ex) {
            return 500;
        }
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
                return body.getJson().getBytes(StandardCharsets.UTF_8);
            case DATA_NOT_SET:
                return new byte[0];
            default:
                // Unsupported body shapes are coerced to their string form so we never drop the response.
                return body.toString().getBytes(StandardCharsets.UTF_8);
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
