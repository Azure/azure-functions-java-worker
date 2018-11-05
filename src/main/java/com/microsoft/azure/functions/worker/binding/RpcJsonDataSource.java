package com.microsoft.azure.functions.worker.binding;

import com.google.gson.Gson;

public final class RpcJsonDataSource extends DataSource<String> {
	public RpcJsonDataSource(String name, String value) { super(name, value, JSON_DATA_OPERATIONS); }

	public static final Gson gson = new Gson();
	private static final DataOperations<String, Object> JSON_DATA_OPERATIONS = new DataOperations<>();

    static {
        JSON_DATA_OPERATIONS.addOperation(String.class, s -> s);
        JSON_DATA_OPERATIONS.addOperation(String[].class, s -> gson.fromJson(s, String[].class));        
    }
}
