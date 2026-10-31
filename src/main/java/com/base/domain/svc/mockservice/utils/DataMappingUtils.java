package com.base.domain.svc.mockservice.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.base.domain.svc.mockservice.to.Criteria;

import org.apache.commons.lang3.StringUtils;

public class DataMappingUtils {

	public String readConfig(String fileName) throws Exception {
		StringBuffer buffer = new StringBuffer();

		try (InputStream configStream = this.getClass().getClassLoader().getResourceAsStream(fileName);
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(configStream));) {
			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
				buffer.append(line);
			}
		} catch (IOException e) {
			throw new Exception(e.getMessage());
		}
		return buffer.toString();
	}

	public static <T> T fetchConfig(String inputStr, Class<T> clazz) throws Exception {
		T retVal = null;
		try {
			Gson gson = new GsonBuilder()
				.registerTypeAdapter(Criteria.class, (JsonDeserializer<Criteria>) (json, typeOfT, context) -> {
					JsonObject obj = json.getAsJsonObject();
					Criteria c = new Criteria();
					if (obj.has("source")) c.setSource(obj.get("source").getAsString());
					if (obj.has("refString")) c.setRefString(obj.get("refString").getAsString());
					if (obj.has("targetAttribute")) c.setTargetAttribute(obj.get("targetAttribute").getAsString());
					if (obj.has("type")) c.setType(obj.get("type").getAsString());
					if (obj.has("datatype")) c.setDataType(obj.get("datatype").getAsString());
					if (obj.has("dataType")) c.setDataType(obj.get("dataType").getAsString());
					if (obj.has("version")) c.setVersion(obj.get("version").getAsString());
					if (obj.has("fieldsToReturn")) {
						JsonElement ftr = obj.get("fieldsToReturn");
						List<String> list = new ArrayList<>();
						if (ftr.isJsonArray()) {
							for (JsonElement e : ftr.getAsJsonArray()) list.add(e.getAsString());
						} else if (ftr.isJsonPrimitive()) {
							list.add(ftr.getAsString());
						}
						c.setFieldsToReturn(list);
					}
					return c;
				})
				.create();
			retVal = gson.fromJson(inputStr, clazz);
		} catch (Exception ie) {
			throw new Exception(ie.getMessage());
		}
		return retVal;
	}

	public static <T> T fetchConfig(String inputStr, Type type) throws Exception {
		T retVal = null;
		try {
			Gson gson = new Gson();
			retVal = gson.fromJson(inputStr, type);
		} catch (Exception ie) {
			throw new Exception(ie.getMessage());
		}
		return retVal;
	}

	public static <T> Set<T> castList(Class<? extends T> clazz, Collection<?> c) {

		Set<T> r = new HashSet<T>(c.size());
		for (Object o : c)
			r.add(clazz.cast(o));
		return r;
	}

	public static void setDataInMap(Object sourceValue, Map<String, Object> targetMap, String targetField) {
		if (!targetField.contains(".")) {
			targetMap.put(targetField, sourceValue);
		} else {
			String fields[] = StringUtils.split(targetField, ".");
			Map<String, Object> innerMap = targetMap;
			for (int index = 0; index < fields.length - 1; index++) {
				Object fieldValue = innerMap.get(fields[index]);
				if (fieldValue == null) {
					Map<String, Object> nestedMap = new HashMap<>();
					innerMap.put(fields[index], nestedMap);
					innerMap = nestedMap;
				} else {
					Map<String, Object> nestedMap = copyStringKeyedMap(fieldValue);
					innerMap.put(fields[index], nestedMap);
					innerMap = nestedMap;
				}
			}
			innerMap.put(fields[fields.length - 1], sourceValue);
		}
	}

	public static Object getDataFromMap(Map<String, Object> sourceMap, String field) {

		Object returnObject = null;
		if (!field.contains(".")) {
			returnObject = sourceMap.get(field);
		} else {
			String fields[] = StringUtils.split(field, ".");
			Map<String, Object> innerMap = sourceMap;
			int index = 0;
			for (; index < fields.length - 1; index++) {
				Object fieldValue = innerMap.get(fields[index]);
				if (fieldValue != null) {
					innerMap = copyStringKeyedMap(fieldValue);
				} else {
					break;
				}
			}
			if (index == fields.length - 1) {
				returnObject = innerMap.get(fields[fields.length - 1].trim());
			}
		}

		if (returnObject instanceof Map<?, ?>) {
			returnObject = copyStringKeyedMap(returnObject);
		}
		return returnObject;
	}

	private static Map<String, Object> copyStringKeyedMap(Object value) {
		if (!(value instanceof Map<?, ?> sourceMap)) {
			throw new IllegalArgumentException("Expected a map while traversing nested data");
		}
		Map<String, Object> copyObject = new HashMap<>();
		for (Entry<?, ?> entry : sourceMap.entrySet()) {
			if (!(entry.getKey() instanceof String key)) {
				throw new IllegalArgumentException("Map keys must be strings");
			}
			if (entry.getValue() instanceof Map<?, ?>)
				copyObject.put(key, copyStringKeyedMap(entry.getValue()));
			else
				copyObject.put(key, entry.getValue());
		}
		return copyObject;
	}

	public String getEnvironmentVariable(String envVar) {
		return System.getenv(envVar);
	}

	public static Object convertDataType(String dataType, Object data) throws Exception {

		Object retVal = null;

		if (dataType.equalsIgnoreCase(ServiceConstants.INT)) {
			retVal = Integer.valueOf((String) data);
		} else if (dataType.equalsIgnoreCase(ServiceConstants.DOUBLE)) {
			retVal = Double.valueOf((String) data);
		} else if (dataType.equalsIgnoreCase(ServiceConstants.FLOAT)) {
			retVal = Float.valueOf((String) data);
		} else if (dataType.equalsIgnoreCase(ServiceConstants.STRING)) {
			retVal = new String((String) data);
		} else if (dataType.equalsIgnoreCase(ServiceConstants.BOOL)) {
			retVal = Boolean.valueOf((String) data);
		} else {
			retVal = data;
		}

		return retVal;
	}

}
