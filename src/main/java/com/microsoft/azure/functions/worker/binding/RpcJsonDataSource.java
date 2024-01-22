package com.microsoft.azure.functions.worker.binding;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.microsoft.azure.functions.worker.Util;
import org.apache.commons.lang3.reflect.TypeUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public final class RpcJsonDataSource extends DataSource<String> {
	public RpcJsonDataSource(String name, String value) {
		super(name, value, JSON_DATA_OPERATIONS);
	}

	public static final Gson gson = Util.getGsonInstance();
	public static final JsonParser gsonParser = new JsonParser();
	private static final DataOperations<String, Object> JSON_DATA_OPERATIONS = new DataOperations<>();

	public static Object convertToStringArrayOrList(String sourceValue, Type targetType) {
		try {
			return gson.fromJson(sourceValue, targetType);
		} catch (JsonSyntaxException ex) {
				List<String> jsonStringArrayList = new ArrayList<String>();
				JsonArray array = gsonParser.parse(sourceValue).getAsJsonArray();
				for (int i = 0; i < array.size(); i++) {				
					jsonStringArrayList.add(array.get(i).toString());
				}
				if (Collection.class.isAssignableFrom(TypeUtils.getRawType(targetType, null))) {
					return jsonStringArrayList;
				}
				String[] jsonStringListAsArray = new String[jsonStringArrayList.size()];
				jsonStringListAsArray = jsonStringArrayList.toArray(jsonStringListAsArray);
				return jsonStringListAsArray;	
		}
	}

	static {
		JSON_DATA_OPERATIONS.addOperation(String.class, s -> s);
		JSON_DATA_OPERATIONS.addGenericOperation(String[].class, (v, t) -> convertToStringArrayOrList(v, t));
	}
}
