package com.microsoft.azure.functions.worker.binding;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.microsoft.azure.functions.rpc.messages.NullableTypes;
import com.microsoft.azure.functions.worker.Constants;
import com.microsoft.azure.functions.worker.Util;
import org.apache.commons.lang3.reflect.TypeUtils;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.HttpResponseMessage.Builder;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;

public final class RpcHttpRequestDataSource extends DataSource<RpcHttpRequestDataSource> {
	public RpcHttpRequestDataSource(String name, RpcHttp value) {
		super(name, null, HTTP_DATA_OPERATIONS);
		this.httpPayload = value;
		this.bodyDataSource = BindingDataStore.rpcSourceFromTypedData(null, this.httpPayload.getBody());
		this.fields = Arrays.asList(
				convertFromNullableMap(this.httpPayload.getNullableHeadersMap()),
				convertFromNullableMap(this.httpPayload.getNullableQueryMap()),
				convertFromNullableMap(this.httpPayload.getNullableParamsMap()));
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
			return convertFromNullableMap(this.parentDataSource.httpPayload.getNullableHeadersMap());
		}

		@Override
		public Map<String, String> getQueryParameters() {
			return convertFromNullableMap(this.parentDataSource.httpPayload.getNullableQueryMap());
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

	private static final DataOperations<RpcHttpRequestDataSource, Object> HTTP_DATA_OPERATIONS = new DataOperations<>();
	static {
		HTTP_DATA_OPERATIONS.addGenericOperation(HttpRequestMessage.class, (v, t) -> {
			Map<TypeVariable<?>, Type> typeArgs = TypeUtils.getTypeArguments(t, HttpRequestMessage.class);
			Type actualType = typeArgs.size() > 0 ? typeArgs.values().iterator().next() : Object.class;
			BindingData bodyData = v.bodyDataSource.computeByType(actualType).orElseThrow(ClassCastException::new);
			return new HttpRequestMessageImpl(v, bodyData.getValue());
		});
	}

	private static Map<String, String> convertFromNullableMap(Map<String, NullableTypes.NullableString> nullableMap) {
		if (Util.isTrue(System.getenv(Constants.NULLABLE_VALUES_ENABLED_APP_SETTING))) {
			return nullableMap.entrySet().stream().collect(
					Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue())
			);
		} else {
			return nullableMap.entrySet().stream()
					.filter(e -> e.getValue().getValue() != null && !e.getValue().getValue().trim().isEmpty())
					.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue()));
		}
	}
}