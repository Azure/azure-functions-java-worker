package com.microsoft.azure.functions.worker.binding;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.reflect.TypeUtils;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.HttpResponseMessage.Builder;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;
import com.sun.net.httpserver.HttpExchange;

public final class RpcHttpRequestDataSource extends DataSource<RpcHttpRequestDataSource> {

	/**
	 * Per-thread captured HttpExchange used by the HTTP proxy dispatch path to
	 * expose the live request body as an {@link InputStream} to functions that
	 * declare {@code HttpRequestMessage<InputStream>}. Set by
	 * {@code InvocationRequestHandler.executeProxiedHttp} before the broker
	 * invocation and cleared in its {@code finally} block.
	 */
	private static final ThreadLocal<HttpExchange> CURRENT_EXCHANGE = new ThreadLocal<>();

	/**
	 * Installs (or clears, when {@code exchange} is {@code null}) the per-thread
	 * {@code HttpExchange} that subsequently-constructed instances will use as
	 * the live request-body source for streaming-input parameters.
	 */
	public static void setCurrentExchange(HttpExchange exchange) {
		if (exchange == null) {
			CURRENT_EXCHANGE.remove();
		} else {
			CURRENT_EXCHANGE.set(exchange);
		}
	}

	public RpcHttpRequestDataSource(String name, RpcHttp value) {
		super(name, null, HTTP_DATA_OPERATIONS);
		this.httpPayload = value;
		this.bodyDataSource = BindingDataStore.rpcSourceFromTypedData(null, this.httpPayload.getBody());
		this.fields = Arrays.asList(this.httpPayload.getHeadersMap(), this.httpPayload.getQueryMap(),
				this.httpPayload.getParamsMap());
		// Snapshot the per-thread exchange (if any) at construction time so it
		// is available later when the HTTP_DATA_OPERATIONS lambda resolves the
		// body type and decides whether to stream from the live request.
		this.capturedExchange = CURRENT_EXCHANGE.get();
		this.setValue(this);
	}

	static class HttpRequestMessageImpl implements HttpRequestMessage {
		private HttpRequestMessageImpl(RpcHttpRequestDataSource parentDataSource, Object body) {
			this.parentDataSource = parentDataSource;
			this.body = body;
		}

		@Override
		public URI getUri() {
			return URI.create(this.parentDataSource.httpPayload.getUrl());
		}

		@Override
		public HttpMethod getHttpMethod() {
			return HttpMethod.value(this.parentDataSource.httpPayload.getMethod());
		}

		@Override
		public Map<String, String> getHeaders() {
			return this.parentDataSource.httpPayload.getHeadersMap();
		}

		@Override
		public Map<String, String> getQueryParameters() {
			return this.parentDataSource.httpPayload.getQueryMap();
		}

		@Override
		public Object getBody() {
			return this.body;
		}

		@Override
		public HttpResponseMessage.Builder createResponseBuilder(HttpStatusType status) {
			return new RpcHttpDataTarget().status(status);
		}

		@Override
		public Builder createResponseBuilder(HttpStatus status) {
			return new RpcHttpDataTarget().status(status);
		}

		private RpcHttpRequestDataSource parentDataSource;
		private Object body;

	}

	private final RpcHttp httpPayload;
	private final DataSource<?> bodyDataSource;
	private final List<Map<String, String>> fields;
	private final HttpExchange capturedExchange;

	private static final DataOperations<RpcHttpRequestDataSource, Object> HTTP_DATA_OPERATIONS = new DataOperations<>();
	static {
		HTTP_DATA_OPERATIONS.addGenericOperation(HttpRequestMessage.class, (v, t) -> {
			Map<TypeVariable<?>, Type> typeArgs = TypeUtils.getTypeArguments(t, HttpRequestMessage.class);
			Type actualType = typeArgs.size() > 0 ? typeArgs.values().iterator().next() : Object.class;
			// Streaming-input path: when the user declares
			// HttpRequestMessage<InputStream> (or any InputStream subtype) and
			// we have a captured HttpExchange (i.e. running under the HTTP
			// proxy), hand the live request body to the function instead of
			// going through the buffered bodyDataSource.
			if (v.capturedExchange != null
					&& actualType instanceof Class<?>
					&& InputStream.class.isAssignableFrom((Class<?>) actualType)) {
				return new HttpRequestMessageImpl(v, v.capturedExchange.getRequestBody());
			}
			BindingData bodyData = v.bodyDataSource.computeByType(actualType).orElseThrow(ClassCastException::new);
			return new HttpRequestMessageImpl(v, bodyData.getValue());
		});
	}
}