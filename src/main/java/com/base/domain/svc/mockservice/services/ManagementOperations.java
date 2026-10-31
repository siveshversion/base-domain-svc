package com.base.domain.svc.mockservice.services;

import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import jakarta.ws.rs.core.MultivaluedMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.base.domain.svc.mockservice.core.ConfiguredRequestProcessor;
import com.base.domain.svc.mockservice.db.MongoDataRepository;
import com.base.domain.svc.mockservice.utils.DataMappingUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ManagementOperations {

	// Log Handle
	private static Logger log = LogManager.getLogger(ConfiguredRequestProcessor.class.getName());

	// Database handle
	MongoDataRepository db = new MongoDataRepository();

	/////////////////////////////////////////////////////////////////////////////////////////////////////////

	// Add a single configuration record
	public String addConfiguration(String configuration) throws Exception {
		return db.createConfiguration(configuration);
	}

	// Add multiple configuration records
	public String addConfigurations(List<String> configurations) throws Exception {
		return db.createConfigurations(configurations);
	}

	// Add multiple configuration records from JSON payload with mapperConf
	// structure
	public String addConfigurationsList(String jsonPayload) throws Exception {
		Type type = new TypeToken<Map<String, List<Map<String, Object>>>>() {
		}.getType();
		Map<String, List<Map<String, Object>>> payload = DataMappingUtils.fetchConfig(jsonPayload, type);

		List<Map<String, Object>> mapperConfList = payload.get("mapperConf");
		if (mapperConfList == null || mapperConfList.isEmpty()) {
			throw new Exception("mapperConf array is required and cannot be empty");
		}

		com.google.gson.Gson gson = new com.google.gson.Gson();
		List<String> configurations = mapperConfList.stream().map(config -> gson.toJson(config))
				.collect(Collectors.toList());

		return db.createConfigurations(configurations);
	}

	// Add all service configurations from nested mapper payload structure
	public String addAllSvcConfigurations(String jsonPayload) throws Exception {
		Type type = new TypeToken<Map<String, List<Map<String, Object>>>>() {
		}.getType();
		Map<String, List<Map<String, Object>>> payload = DataMappingUtils.fetchConfig(jsonPayload, type);

		List<Map<String, Object>> mapperList = payload.get("mapper");
		if (mapperList == null || mapperList.isEmpty()) {
			throw new Exception("mapper array is required and cannot be empty");
		}

		com.google.gson.Gson gson = new com.google.gson.Gson();
		List<String> allConfigurations = new ArrayList<>();

		for (Map<String, Object> service : mapperList) {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> mapperConfList = (List<Map<String, Object>>) service.get("mapperConf");
			if (mapperConfList != null && !mapperConfList.isEmpty()) {
				List<String> serviceConfigs = mapperConfList.stream().map(config -> gson.toJson(config))
						.collect(Collectors.toList());
				allConfigurations.addAll(serviceConfigs);
			}
		}

		return db.createConfigurations(allConfigurations);
	}

	public String fetchMatchingRecords(MultivaluedMap<String, String> filters) throws Exception {
		// Extract the key-values in a usable format
		Map<String, String> parameters = this.extractValue(filters);

		return this.getReturnValue(db.fetchConfigurations(parameters.keySet().toArray(new String[0]),
				parameters.values().toArray(new String[0])));
	}

	public String fetchMatchingRecordsWithPagination(MultivaluedMap<String, String> filters, int pageNum, int pageSize)
			throws Exception {

		// Extract the key-values in a usable format
		Map<String, String> parameters = this.extractValue(filters);
		parameters.remove("pagenum");
		parameters.remove("pageSize");

		return this.getReturnValue(db.fetchConfigurationsWithPagination(parameters.keySet().toArray(new String[0]),
				parameters.values().toArray(new String[0]), pageNum, pageSize));
	}

	public long fetchMatchingConfigurationRecordCount(MultivaluedMap<String, String> filters) throws Exception {

		// Extract the key-values in a usable format
		Map<String, String> parameters = this.extractValue(filters);

		return db.fetchMatchingConfigurationRecordCount(parameters.keySet().toArray(new String[0]),
				parameters.values().toArray(new String[0]));
	}

	public String updateConfiguration(String id, String data) throws Exception {
		return db.updateConfigurationById(id, data);
	}

	public String deleteConfiguration(Map<String, String> filters) throws Exception {
		return db.deleteConfiguration(filters.keySet().toArray(new String[0]), filters.values().toArray(new String[0]));
	}

	public long deleteConfigurationwithId(String id) throws Exception {
		return db.deleteConfigurationById(id);
	}

	public String createConfigurationIndexes(String fields, boolean isUnique) throws Exception {

		Type type = new TypeToken<ArrayList<String>>() {
		}.getType();
		List<String> fieldNames = new DataMappingUtils().fetchConfig(fields, type);
		return db.createConfigurationIndexes(fieldNames.toArray(new String[0]), isUnique);
	}

	public String listConfigurationIndexes() throws Exception {

		return this.getReturnValue(db.listConfigurationIndexes());
	}

	public void dropConfigurationIndexes(String indexName) throws Exception {
		db.dropConfigurationIndex(indexName);
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////

	// Add data record
	public String addData(String collection, String data) throws Exception {
		return db.create(collection, data);
	}

	// Add multiple data records from JSON array payload
	public String addDataRecordsList(String collection, String jsonPayload) throws Exception {
		Type type = new TypeToken<List<Map<String, Object>>>() {
		}.getType();
		List<Map<String, Object>> recordsList = DataMappingUtils.fetchConfig(jsonPayload, type);

		if (recordsList == null || recordsList.isEmpty()) {
			throw new Exception("Records array is required and cannot be empty");
		}

		com.google.gson.Gson gson = new com.google.gson.Gson();
		List<String> insertResults = new ArrayList<>();

		for (Map<String, Object> record : recordsList) {
			String recordJson = gson.toJson(record);
			String result = db.create(collection, recordJson);
			insertResults.add(result);
		}

		return gson.toJson(Map.of("inserted", insertResults.size(), "results", insertResults));
	}

	// Delete data record with id
	public long deleteDataWithId(String collection, String id) throws Exception {
		return db.deleteById(collection, id);
	}

	public String readMatchingDataRecords(String collection, Map<String, String> filters) throws Exception {
		return this.getReturnValue(db.readAll(collection, filters.keySet().toArray(new String[0]),
				filters.values().toArray(new String[0]), null));
	}

	public String readMatchingDataRecordsWithPagination(String collection, MultivaluedMap<String, String> filters,
			int pageNum, int pageSize) throws Exception {

		// Extract the key-values in a usable format
		Map<String, String> parameters = this.extractValue(filters);

		parameters.remove("pagenum");
		parameters.remove("pageSize");
		parameters.remove("table");

		return this.getReturnValue(db.readRecordsWithPagination(collection, parameters.keySet().toArray(new String[0]),
				parameters.values().toArray(new String[0]), pageNum, pageSize));
	}

	public long readMatchingDataRecordCount(String collection, MultivaluedMap<String, String> filters)
			throws Exception {

		// Extract the key-values in a usable format
		Map<String, String> parameters = this.extractValue(filters);
		parameters.remove("table");

		return db.readMatchingRecordCount(collection, parameters.keySet().toArray(new String[0]),
				parameters.values().toArray(new String[0]));
	}

	public String updateDataRecords(String collection, String id, String data) throws Exception {
		return db.updateRecordById(collection, id, data);
	}

	public String createIndexes(String collection, String fields, boolean isUnique) throws Exception {

		Type type = new TypeToken<ArrayList<String>>() {
		}.getType();
		List<String> fieldNames = new DataMappingUtils().fetchConfig(fields, type);
		return db.createIndexes(collection, fieldNames.toArray(new String[0]), isUnique);

	}

	public String listIndexes(String collection) throws Exception {
		return this.getReturnValue(db.listIndexes(collection));
	}

	public void dropIndexes(String collection, String indexName) throws Exception {
		db.dropIndex(collection, indexName);
	}

	public void dropCollection(String collectionName) throws Exception {
		db.dropCollection(collectionName);
	}

	public Map<String, Object> dropCollections(String collectionsJson) throws Exception {
		Type type = new TypeToken<List<String>>() {
		}.getType();
		List<String> collections = DataMappingUtils.fetchConfig(collectionsJson, type);

		Map<String, Object> deleteResult = new HashMap<>();
		List<Map<String, Object>> collectionResults = new ArrayList<>();
		int successCount = 0;

		for (String collection : collections) {
			Map<String, Object> result = new HashMap<>();
			result.put("collection", collection);

			try {
				db.dropCollection(collection);
				result.put("status", "deleted");
				successCount++;
			} catch (Exception e) {
				log.error("Failed to drop collection: " + collection, e);
				result.put("status", "failed");
				result.put("error", e.getMessage());
			}

			collectionResults.add(result);
		}

		deleteResult.put("totalCollections", collections.size());
		deleteResult.put("successCount", successCount);
		deleteResult.put("failedCount", collections.size() - successCount);
		deleteResult.put("results", collectionResults);

		return deleteResult;
	}

	public String getCollectionList() throws Exception {
		return this.getReturnValue(db.listCollections());
	}

	// Data migration from source to target
	public Map<String, Object> migrateData(String tablesJson, String sourceUrl) throws Exception {
		Type type = new TypeToken<List<String>>() {
		}.getType();
		List<String> tables = DataMappingUtils.fetchConfig(tablesJson, type);

		Map<String, Object> migrationResult = new HashMap<>();
		List<Map<String, Object>> tableResults = new ArrayList<>();
		int totalRecords = 0;

		for (String table : tables) {
			Map<String, Object> tableResult = new HashMap<>();
			tableResult.put("table", table);

			try {
				// Get record count from source
				String countUrl = sourceUrl + "/mock/data-records/fetch-record-count?table=" + table;
				String countResponse = fetchFromUrl(countUrl);
				int recordCount = Integer.parseInt(countResponse.trim());

				if (recordCount == 0) {
					tableResult.put("status", "skipped");
					tableResult.put("records", 0);
					tableResults.add(tableResult);
					continue;
				}

				// Fetch data from source with dynamic page size
				int pageSize = Math.min(recordCount, 1000);
				String sourceDataUrl = sourceUrl + "/mock/data-records/paging?table=" + table + "&pageSize=" + pageSize;
				String sourceData = fetchFromUrl(sourceDataUrl);

				// Insert into target database
				String insertResult = addDataRecordsList(table, sourceData);

				tableResult.put("status", "success");
				tableResult.put("records", recordCount);
				totalRecords += recordCount;

			} catch (Exception e) {
				log.error("Migration failed for table: " + table, e);
				tableResult.put("status", "failed");
				tableResult.put("error", e.getMessage());
			}

			tableResults.add(tableResult);
		}

		migrationResult.put("totalTables", tables.size());
		migrationResult.put("totalRecords", totalRecords);
		migrationResult.put("results", tableResults);

		return migrationResult;
	}

	private String fetchFromUrl(String urlString) throws Exception {
		java.net.URL url = java.net.URI.create(urlString).toURL();
		java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(30000);
		conn.setReadTimeout(30000);

		int responseCode = conn.getResponseCode();
		if (responseCode != 200) {
			throw new Exception("Failed to fetch from URL: " + urlString + ", Response code: " + responseCode);
		}

		java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;

		while ((line = in.readLine()) != null) {
			response.append(line);
		}
		in.close();

		return response.toString();
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Bulk insert method that handles both JSON arrays and CSV data Adds
	 * auto-generated questionId to each record
	 */
	public String bulkInsert(String tableName, String inputData, String autogenFieldName) throws Exception {
		if (tableName == null || tableName.trim().isEmpty() || inputData == null || inputData.trim().isEmpty()) {
			throw new Exception("Invalid input - table name and data are required");
		}

		// Default questionId field name if not provided
		String idField = (autogenFieldName != null && !autogenFieldName.trim().isEmpty()) ? autogenFieldName : "id";

		List<String> jsonRecords = new ArrayList<>();
		Gson gson = new Gson();

		// Check if input is JSON array or CSV
		if (inputData.trim().startsWith("[")) {
			// Handle JSON array input
			Type listType = new TypeToken<List<Map<String, Object>>>() {
			}.getType();
			List<Map<String, Object>> records = gson.fromJson(inputData, listType);

			// Get the starting ID based on existing records
			String startingId = generatetempID(tableName, idField);
			int baseId = Integer.parseInt(startingId);

			for (int i = 0; i < records.size(); i++) {
				Map<String, Object> record = records.get(i);
				record.put(idField, String.format("%06d", baseId + i));
				jsonRecords.add(gson.toJson(record));
			}
		} else {
			// Handle CSV input
			jsonRecords = convertCsvToJson(inputData, idField, tableName);
		}

		// Bulk insert all records
		StringBuilder result = new StringBuilder();
		int successCount = 0;
		int errorCount = 0;

		for (String jsonRecord : jsonRecords) {
			try {
				db.create(tableName, jsonRecord);
				successCount++;
			} catch (Exception e) {
				errorCount++;
				log.error("Failed to insert record: " + jsonRecord, e);
			}
		}

		Map<String, Object> response = new HashMap<>();
		response.put("totalRecords", jsonRecords.size());
		response.put("successCount", successCount);
		response.put("errorCount", errorCount);
		response.put("tableName", tableName);
		response.put("autogenFieldName", idField);

		return gson.toJson(response);
	}

	/**
	 * Convert CSV data to JSON records
	 */
	private List<String> convertCsvToJson(String csvData, String autogenFieldName, String tableName) throws Exception {
		List<String> jsonRecords = new ArrayList<>();
		Gson gson = new Gson();

		// Preprocess CSV to handle HTML entities and malformed quotes
		String cleanedCsv = csvData.replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");

		try (StringReader reader = new StringReader(cleanedCsv)) {
			CSVFormat format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
					.setIgnoreEmptyLines(true).setTrim(true).setAllowMissingColumnNames(true)
					.setIgnoreSurroundingSpaces(true).get();
			CSVParser parser = format.parse(reader);

			// Get the starting ID based on existing records
			String startingId = generatetempID(tableName, autogenFieldName);
			int baseId = Integer.parseInt(startingId);

			int recordIndex = 0;
			for (CSVRecord record : parser) {
				Map<String, Object> jsonRecord = new HashMap<>();

				// Add all CSV fields
				for (String header : parser.getHeaderNames()) {
					jsonRecord.put(header, record.get(header));
				}

				// Add auto-generated ID
				jsonRecord.put(autogenFieldName, String.format("%06d", baseId + recordIndex));

				jsonRecords.add(gson.toJson(jsonRecord));
				recordIndex++;
			}
		}

		return jsonRecords;
	}

	/**
	 * Generate auto-incremented ID by checking existing records
	 */
	private String generatetempID(String collection, String idField) throws Exception {
		// Get all existing records to find the highest ID
		List<String> existingRecords = db.readAll(collection, new String[0], new Object[0], List.of(idField));

		int maxId = 0;
		Gson gson = new Gson();

		// Parse existing records to find the highest ID
		for (String record : existingRecords) {
			Map<String, Object> recordMap = gson.fromJson(record,
					new TypeToken<Map<String, Object>>() {}.getType());
			Object idValue = recordMap.get(idField);

			if (idValue != null) {
				try {
					int currentId = Integer.parseInt(idValue.toString());
					if (currentId > maxId) {
						maxId = currentId;
					}
				} catch (NumberFormatException e) {
					// Skip non-numeric IDs
				}
			}
		}

		// Return next ID in sequence
		return String.format("%06d", maxId + 1);
	}

	String generateAutoGenID(String collection, String idField, String desiredPrefix, int minNumberWidth)
			throws Exception {
		return db.generateDynamicAutoGenIDAtomic(collection, idField, desiredPrefix, minNumberWidth);
	}

	// Prepare content for returning back to caller
	private String getReturnValue(List<String> records) {
		// Holds the value that will be send back to the client
		String retVal = null;

		if (records != null && !records.isEmpty()) {
			StringBuffer buf = new StringBuffer();
			for (int i = 0; i < records.size(); i++) {
				if (i == 0) {
					buf.append("[");
				} else {
					buf.append(",");
				}
				buf.append(records.get(i));
			}
			buf.append("]");
			retVal = buf.toString();
		}

		return retVal;
	}

	private Map<String, String> extractValue(MultivaluedMap<String, String> filters) {

		Map<String, String> parameters = new HashMap<String, String>();

		Iterator<String> it = filters.keySet().iterator();

		while (it.hasNext()) {
			String theKey = it.next();
			parameters.put(theKey, filters.getFirst(theKey));
		}

		//
		// Alternative method to do the same
		//
		// return filters.entrySet().stream()
		// .collect(
		// Collectors.toMap(
		// Map.Entry::getKey, entry ->
		// entry.getValue().stream().findFirst().orElse(null)));

		return parameters;
	}

}
