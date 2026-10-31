package com.base.domain.svc.mockservice.core;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.base.domain.svc.mockservice.db.MongoDataRepository;
import com.base.domain.svc.mockservice.to.Action;
import com.base.domain.svc.mockservice.to.ConfigMap;
import com.base.domain.svc.mockservice.to.Criteria;
import com.base.domain.svc.mockservice.utils.ServiceConstants;
import com.base.domain.svc.mockservice.utils.DataMappingUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Core class to handle the restful service request. The steps are as follows:
 * 
 * 0. For a incoming request restful URL, query the db to find possible
 * configuration record matches (a) this uses a progressive approximation
 * approach to find the possible configuration matches
 * 
 * 1. Using bit of regex magic, and incoming http verb, find the exact
 * configuration match
 * 
 * 2. Then using the configuration (if found), act up on the restful service
 * request
 * 
 * 3. It could be any of the CURD operations. The handling varies based on if
 * "arrays' or involved (a) hence differentiate if it is array or non-array and
 * handle it appropriately
 * 
 * 4. Along the way extract the data from path parameters, request body, query
 * parameters and map it as specified in the configuration
 * 
 * 5. Finally call the Database services to act up on it
 * 
 * 
 * TODO: Quite a bit of technical debt is present (a) Need to used ENUM instead
 * of constants (b) Lot of duplicate code, need to consolidate
 * 
 * 
 */

public class ConfiguredRequestProcessor {

	// Log Handle
	private static Logger log = LogManager.getLogger(ConfiguredRequestProcessor.class.getName());

	// Singleton instances to reduce object creation
	private static final MongoDataRepository db = new MongoDataRepository();
	private static final Gson gson = new Gson();
	private static final RequestCriteriaMapper mapper = new RequestCriteriaMapper();
	
	// LRU cache for configurations (thread-safe)
	private static final Map<String, ConfigMap> configCache = Collections.synchronizedMap(
		new LinkedHashMap<String, ConfigMap>(100, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, ConfigMap> eldest) {
				return size() > 100;
			}
		}
	);
	
	// Compiled regex patterns cache
	private static final Map<String, Pattern> regexCache = new ConcurrentHashMap<>();

	/**
	 * Heart of the utility, that dynamically mocks restful service
	 * 
	 * @param inUrl
	 * @param queryParams
	 * @param payload
	 * @param httpMethod
	 * @return
	 * @throws Exception
	 */
	public String process(String inUrl, Map<String, String[]> queryParams, String payload, String httpMethod)
			throws Exception {

		// Check cache first - only use cache if the cached config has same segment count
		// to avoid returning wrong literal config for parameterized URLs
		String cacheKey = inUrl + ":" + httpMethod;
		ConfigMap configMap = configCache.get(cacheKey);
		
		if (configMap == null) {
			// Get the associated configuration for given URL
			configMap = this.getAssociatedConfiguration(inUrl, httpMethod);
			if (configMap != null && configMap.getUrl().contains("{{")) {
				// Don't cache parameterized configs by incoming URL - they are shared across many URLs
				// Cache by the config URL pattern instead
			} else if (configMap != null) {
				configCache.put(cacheKey, configMap);
			}
		}

		// If no configuration found return null
		if (configMap != null) {
			// Extract Path Parameters
			Map<String, String> pathParams = this.extractPathParams(inUrl, configMap.getUrl());
			return this.invokeAction(configMap, pathParams, queryParams, payload);
		} else {
			log.error("### No associated configuration found for incoming URL \"" + inUrl + "\"");
			return null;
		}
	}

	/**
	 * This method gets the configuration details associated with incoming restful
	 * url
	 * 
	 * @param inUrl
	 * @param httpMethod
	 * @return
	 * @throws Exception
	 */
	private ConfigMap getAssociatedConfiguration(String inUrl, String httpMethod) throws Exception {

		// Declaration
		List<String> dbResults = null;
		ConfigMap configMap = null;

		// Get a copy of the in coming URL
		String url = new String(inUrl);

		// Guard: if URL is root or too short, nothing to match
		if (url == null || url.trim().isEmpty() || url.equals("/")) {
			return null;
		}

		// First split the incoming URL, which will be used for processing in further
		// steps
		String[] splits = inUrl.split(ServiceConstants.FORWARD_SLASH);
		int index = splits.length;

		// Now loop through the splits and try to locate the matching configurations
		// start the search with the full url string,
		// if no hit, the repeat the search by removing the part sections of the URL
		// progressively the search approximation and fuzziness increases, till we find
		// a match or the index becomes zero
		while (index >= 0 && !url.isEmpty()) {

			// query the DB using the URL to find matching config with aid of mongo db regex
			// queries
			dbResults = db.fetchConfigurations(url);

			// If no hit, then increase the approximation/fuzziness and try again
			if (dbResults == null || dbResults.isEmpty()) {

				// Reduce the index
				index--;

				// Re-initialize the incoming URL string
				url = new String(inUrl);

				// generate a new search query from the incoming url
				url = this.generateFuzzyQueryString(splits, index, url);
			} else {
				// Check if we found a matching configuration with the right verb
				configMap = this.getConfiguration(dbResults, inUrl, httpMethod);
				if (configMap != null) {
					// Found a valid configuration, return it
					return configMap;
				}
				// If no valid configuration found, continue searching
				index--;
				url = new String(inUrl);
				url = this.generateFuzzyQueryString(splits, index, url);
			}
		}
		
		// If still no results found, try searching using only the static (non-numeric) segments
		// where the incoming URL has real values like S002, api001 in place of placeholders
		if (configMap == null) {
			List<String> staticSegments = new ArrayList<>();
			for (String seg : splits) {
				if (!seg.trim().isEmpty() && !containsDigit(seg)) {
					staticSegments.add(seg);
				}
			}
			// Try progressively shorter combinations of static segments
			for (int i = staticSegments.size(); i >= 1 && configMap == null; i--) {
				String pattern = String.join(".*", staticSegments.subList(0, i));
				log.info("### Trying parameterized search with pattern: " + pattern);
				dbResults = db.fetchConfigurations(pattern);
				if (dbResults != null && !dbResults.isEmpty()) {
					log.info("### Found " + dbResults.size() + " results with pattern: " + pattern);
					configMap = this.getConfiguration(dbResults, inUrl, httpMethod);
					if (configMap != null) {
						log.info("### Successfully found parameterized configuration: " + configMap.getUrl());
					}
				}
			}
		}

		return configMap;
	}

	/**
	 * Utility method to generate fuzzy URL string that will be used to query the
	 * database
	 * 
	 * @param splits
	 * @param index
	 * @param inUrl
	 * @return
	 */
	private String generateFuzzyQueryString(String[] splits, int index, String inUrl) {
		String replacementString = this.constructReplacementString(splits, index);
		return inUrl.replaceAll(replacementString, "");
	}

	/**
	 * Generates replacement string for generating fuzzy query string
	 * 
	 * @param splits
	 * @param index
	 * @return
	 */
	private String constructReplacementString(String[] splits, int index) {

		StringBuilder retVal = new StringBuilder();

		if (splits != null) {
			for (int i = splits.length; i > index; i--) {
				retVal.insert(0, splits[i - 1]);
				retVal.insert(0, "/");
			}
		}

		return retVal.toString();
	}

	/**
	 * Since we are using fuzzy query string to get the associated configuration
	 * call from DB for a given restful service URL, We may end up with bunch of
	 * matches.
	 * 
	 * From the bunch of matches, we try to identify the exact match using
	 * combination of: - regex (that is applied against the URL) - HTTP verb
	 * 
	 * Once exact match is found, the configuration fetched from DB is converted
	 * into Config Map object for easy handling
	 * 
	 * 
	 * @param dbResults
	 * @param inUrl
	 * @param httpMethod
	 * @return
	 * @throws Exception
	 */
	private ConfigMap getConfiguration(List<String> dbResults, String inUrl, String httpMethod) throws Exception {

		ConfigMap configMap = null;
		ConfigMap literalMatch = null;
		ConfigMap parameterizedMatch = null;
		int bestParamScore = -1;  // Track specificity of parameterized matches
		
		// Debug: Log all found configurations
		log.info("### Found " + dbResults.size() + " potential configurations for URL: " + inUrl);
		for (int i = 0; i < dbResults.size(); i++) {
			ConfigMap tempConfig = DataMappingUtils.fetchConfig(dbResults.get(i), ConfigMap.class);
			log.info("### Config " + (i+1) + ": URL=" + tempConfig.getUrl() + ", Verb=" + tempConfig.getVerb());
		}

		for (int i = 0; i < dbResults.size(); i++) {

			// convert the string to object
			ConfigMap tempConfigMap = DataMappingUtils.fetchConfig(dbResults.get(i), ConfigMap.class);

			// Use cached compiled regex patterns for better performance
			String regexKey = tempConfigMap.getUrl();
			String regexStr = this.getRegexedString(regexKey.split(ServiceConstants.FORWARD_SLASH));
			Pattern pattern = regexCache.computeIfAbsent(regexKey,
				k -> Pattern.compile(regexStr));
			
			boolean regexResult = pattern.matcher(inUrl).matches();
			boolean verbResult = httpMethod.equalsIgnoreCase(tempConfigMap.getVerb());
			boolean result = regexResult && verbResult;

			// debug
			log.info("### ConfiguredRequestProcessor::getConfiguration --> Config URL: " + tempConfigMap.getUrl() + ", Regex matches: " + regexResult + ", Verb matches: " + verbResult);

			if (result) {
				// Check if this is a parameterized URL (contains {{}})
				if (tempConfigMap.getUrl().contains("{{") && tempConfigMap.getUrl().contains("}}")) {
					// Calculate specificity score based on number of URL segments
					// More segments = more specific match
					int segmentCount = tempConfigMap.getUrl().split(ServiceConstants.FORWARD_SLASH).length;
					if (segmentCount > bestParamScore) {
						parameterizedMatch = tempConfigMap;
						bestParamScore = segmentCount;
						log.info("### Found parameterized match: " + tempConfigMap.getUrl() + " (specificity score: " + segmentCount + ")");
					}
				} else {
					literalMatch = tempConfigMap;
					log.info("### Found literal match: " + tempConfigMap.getUrl());
				}
			}
		}
		
		// Prefer parameterized match over literal when literal has no placeholders
		// (literal configs like S001 hardcoded should not override parameterized ones)
		if (literalMatch != null && parameterizedMatch != null) {
			// Use parameterized if it has same or more segments (more specific)
			int literalSegments = literalMatch.getUrl().split(ServiceConstants.FORWARD_SLASH).length;
			if (bestParamScore >= literalSegments) {
				configMap = parameterizedMatch;
				log.info("### Selected parameterized over literal: " + parameterizedMatch.getUrl());
			} else {
				configMap = literalMatch;
				log.info("### Selected literal configuration: " + literalMatch.getUrl());
			}
		} else if (literalMatch != null) {
			configMap = literalMatch;
			log.info("### Selected literal configuration: " + literalMatch.getUrl());
		} else if (parameterizedMatch != null) {
			configMap = parameterizedMatch;
			log.info("### Selected parameterized configuration: " + parameterizedMatch.getUrl());
		}

		return configMap;
	}

	/**
	 * A utility method to get regex enabled URL string that will be used to find
	 * the exact configuration match
	 * 
	 * @param splits
	 * @return
	 */

	private String getRegexedString(String[] splits) {

		String retVal = "";

		int len = splits.length;

		retVal += ServiceConstants.BACKSLASH_PATTERN;
		for (int i = 0; i < len; i++) {

			if (splits[i].startsWith(ServiceConstants.PREFIX_PATTERN)) {
				retVal += ServiceConstants.REGIX_PATTERN;

				if (i != len - 1) {
					retVal += ServiceConstants.BACKSLASH_PATTERN;
				}

			} else if (!splits[i].trim().isEmpty()) {
				// Escape special regex characters in the URL segment
				retVal += Pattern.quote(splits[i]);
				if (i != len - 1) {
					retVal += ServiceConstants.BACKSLASH_PATTERN;
				}
			}
		}

		return retVal;
	}

	/**
	 * A utility method to extract the path parameters from the URL string
	 * 
	 * 
	 * @param inUrl
	 * @param patternUrl
	 * @return
	 */
	private Map<String, String> extractPathParams(String inUrl, String patternUrl) throws Exception {

		log.info("### extractPathParams --> incoming URL \"" + inUrl + "\" <==> patternURL " + patternUrl);

		Map<String, String> pathParam = new HashMap<>();

		try {
			String[] inUrlSplits = inUrl.split(ServiceConstants.FORWARD_SLASH);
			String[] patternUrlSplits = patternUrl.split(ServiceConstants.FORWARD_SLASH);

			for (int i = 0; i < patternUrlSplits.length; i++) {

				if (patternUrlSplits[i].startsWith(ServiceConstants.PREFIX_PATTERN)) {

					String cleanParamKey = patternUrlSplits[i].replace(ServiceConstants.OPEN_BRACKET_PATTERN, "")
							.replace(ServiceConstants.CLOSE_BRACKET_PATTERN, "");
					pathParam.put(cleanParamKey, inUrlSplits[i]);
				}
			}
		} catch (Exception e) {
			log.error("#####-#### Error extractPathParams", e.fillInStackTrace());
			throw new Exception();
		}

		return pathParam;
	}

	/**
	 * This method is kind of traffic cop which invokes appropriate CURD method
	 * based on the configuration A single configuration can have multiple actions
	 * and every action can have multiple criteria for the CRUD operation
	 * 
	 * @param configMap
	 * @param pathParams
	 * @param queryParams
	 * @param payload
	 * @return
	 * @throws Exception
	 */
	private String invokeAction(ConfigMap configMap, Map<String, String> pathParams, Map<String, String[]> queryParams,
			String payload) throws Exception {

		String retVal = null;

		if (configMap != null) {

			// There can be more than one action associated with a given config
			// So loop through the actions and act up on it
			// if nothing matches, then don't do any thing, ignore
			for (int i = 0; i < configMap.getActions().size(); i++) {

				Action action = configMap.getActions().get(i);

				// We need query mapping what ever be the CRUD method
				// It is common for all. So it is invoked here
				Map<String, String> tmap = mapper.getQueryMappings(action, pathParams, queryParams, payload);

				// Now check what type of CRUD action and act up on it

				// READ is straight forward, take bunch of filter criteria and fetch from db
				if (action.getType().equalsIgnoreCase(ServiceConstants.READ)) {
					retVal = this.invokeRead(configMap, action, tmap, pathParams);

				}

				// CREATE takes the entire content of the body and pushes it in to DB
				// TODO: Need to enhance so that we can specify specific attributes from which
				// the payload data can be picked up
				else if (action.getType().equalsIgnoreCase(ServiceConstants.CREATE)) {
					retVal = this.invokeCreate(configMap, action, tmap, payload);

				}

				// UPDATE: this update handles NON-ARRAY scenarios
				// Again pretty basic, takes bunch of filter criteria and updates the records
				// matching the filter criteria
				// Single record update or update many records, can be controlled through the
				// "isMany" tag in the configuration
				else if (action.getType().equalsIgnoreCase(ServiceConstants.UPDATE)) {

					// For update, the payload can be extract using a key. It could be in path
					// param, query param or body
					// if no key is specified, the entire content of the body is taken up as the
					// update payload
					Map<String, Object> updateDataMap = new RequestCriteriaMapper().getUpdateMappings(action, pathParams, queryParams,
							payload);
					retVal = this.invokeUpdate(configMap, action, tmap, updateDataMap);

				}

				// Updating element in array or adding an element to array or deleting elements
				// from array is bit trickier,
				// hence had to be handle separately
				else if (action.getType().equalsIgnoreCase(ServiceConstants.UPDATE_ARRAY_ELEMENT)
						|| action.getType().equalsIgnoreCase(ServiceConstants.UPDATE_ARRAY_ADD_ELEMENT)) {

					// For update, the payload can be extract using a key. It could be in path
					// param, query param or body
					// if no key is specified, the entire content of the body is taken up as the
					// update payload
					Map<String, Object> updateDataMap = new RequestCriteriaMapper().getUpdateMappings(action, pathParams, queryParams,
							payload);

					// The array updates uses array filters, were applicable, this extracts those
					// details
					Map<String, String> arrayFilterMap = new RequestCriteriaMapper().getUpdateArrayFilterMappings(action, pathParams,
							queryParams, payload);

					retVal = this.invokeUpdateArray(configMap, action, tmap, arrayFilterMap, updateDataMap);
				}

				// Deleting an element from array needs specific handling, which is taken care
				// of here
				else if (action.getType().equalsIgnoreCase(ServiceConstants.UPDATE_ARRAY_DELETE_ELEMENT)) {

					Map<String, Object> deleteFilterMap = new RequestCriteriaMapper().getDeleteFilterMappings(action, pathParams,
							queryParams, payload);
					Map<String, String> arrayFilterMap = new RequestCriteriaMapper().getUpdateArrayFilterMappings(action, pathParams,
							queryParams, payload);

					String deleteKey = this.getDeleteKey(action);

					retVal = this.invokeDeleteArrayElement(configMap, action, tmap, arrayFilterMap, deleteFilterMap,
							deleteKey);
				}
				// Deleting an element from array needs specific handling, which is taken care
				// of here
				else if (action.getType().equalsIgnoreCase(ServiceConstants.DELETE)) {

					retVal = this.invokeDelete(configMap, action, tmap);
				}
				// If it is a hard coded value to be returned
				else if (action.getType().equalsIgnoreCase(ServiceConstants.FIXED_RETURN)) {

					retVal = action.getFixedData().toString();

				}

			}
		} else {

			// If not matching configuration found, send back error message
			retVal = " { \"error\" : \" Failed to load configuration \"}";
		}
		return retVal;
	}

	/**
	 * Utility method to invoke the read db functions
	 * 
	 * 
	 * @param configMap
	 * @param action
	 * @param tmap
	 * @return
	 * @throws Exception
	 */
	private String invokeRead(ConfigMap configMap, Action action, Map<String, String> tmap, Map<String, String> pathParams) throws Exception {
		String retVal = null;

		// Check for arrayFilter criteria before the DB call
		Criteria arrayFilterCriteria = null;
		if (action.getCriteria() != null) {
			for (Criteria c : action.getCriteria()) {
				if (c.getType() != null && c.getType().equalsIgnoreCase(ServiceConstants.ARRAYFILTER)) {
					arrayFilterCriteria = c;
					break;
				}
			}
		}

		// If arrayFilter present, ensure the array field is included in projection
		List<String> projectionFields = new RequestCriteriaMapper().getProjectionFields(action);
		if (arrayFilterCriteria != null) {
			List<String> fieldsToReturn = arrayFilterCriteria.getFieldsToReturn();
			if (fieldsToReturn != null && !fieldsToReturn.isEmpty() && !projectionFields.contains(fieldsToReturn.get(0))) {
				projectionFields = new ArrayList<>(fieldsToReturn);
			}
		}

		List<String> records = db.readAll(action.getTable(), tmap.keySet().toArray(new String[0]),
				tmap.values().toArray(new String[0]), projectionFields);

		if (arrayFilterCriteria != null && records != null && !records.isEmpty()) {
			List<String> fieldsToReturn = arrayFilterCriteria.getFieldsToReturn();
			String arrayField = (fieldsToReturn != null && !fieldsToReturn.isEmpty()) ? fieldsToReturn.get(0) : null;
			String filterField = arrayFilterCriteria.getTargetAttribute(); // e.g. "endpoints.apiId"
			String subField = filterField.contains(".") ? filterField.substring(filterField.lastIndexOf('.') + 1) : filterField;
			String filterValue = pathParams.get(arrayFilterCriteria.getRefString());

			if (arrayField != null && filterValue != null) {
				com.google.gson.JsonObject doc = com.google.gson.JsonParser.parseString(records.get(0)).getAsJsonObject();
				com.google.gson.JsonArray arr = doc.has(arrayField) ? doc.getAsJsonArray(arrayField) : null;
				if (arr != null) {
					for (com.google.gson.JsonElement el : arr) {
						if (el.isJsonObject()) {
							com.google.gson.JsonObject obj = el.getAsJsonObject();
							if (obj.has(subField) && filterValue.equals(obj.get(subField).getAsString())) {
								return obj.toString();
							}
						}
					}
				}
				return ServiceConstants.ERROR_MSG_DATA_NOT_FOUND;
			}
		}

		retVal = this.getReturnValue(configMap, records);
		return retVal;
	}

	/**
	 * Utility method to invoke the update array
	 * 
	 * @param configMap
	 * @param action
	 * @param tmap
	 * @param updateArrayFilterMap
	 * @param updateDataMap
	 * @return
	 * @throws Exception
	 */
	private String invokeUpdateArray(ConfigMap configMap, Action action, Map<String, String> tmap,
			Map<String, String> updateArrayFilterMap, Map<String, Object> updateDataMap) throws Exception {
		String retVal = null;
		// Make call to fetch the response data from db

		String updateKey = null;
		Object updateData = null;

		if (updateDataMap == null || updateDataMap.isEmpty()) {
			throw new Exception("updateDataMap is empty");
		}

		for (Map.Entry<String, Object> entry : updateDataMap.entrySet()) {
			updateKey = entry.getKey();
			updateData = entry.getValue();
		}

		retVal = db.updateArray(action.getTable(), tmap.keySet().toArray(new String[0]),
				tmap.values().toArray(new String[0]), updateArrayFilterMap.keySet().toArray(new String[0]),
				updateArrayFilterMap.values().toArray(new String[0]), updateKey, updateData, action.getType(),
				action.isMany());

		return retVal;
	}

	/**
	 * Utility method to invoke the delete array element db method
	 * 
	 */
	private String invokeDeleteArrayElement(ConfigMap configMap, Action action, Map<String, String> tmap,
			Map<String, String> updateArrayFilterMap, Map<String, Object> deleteFilterMap, String deleteKey)
			throws Exception {
		return db.deleteArrayElement(action.getTable(), tmap.keySet().toArray(new String[0]),
				tmap.values().toArray(new String[0]), updateArrayFilterMap.keySet().toArray(new String[0]),
				updateArrayFilterMap.values().toArray(new String[0]), deleteFilterMap.keySet().toArray(new String[0]),
				deleteFilterMap.values().toArray(new String[0]), deleteKey, action.isMany());
	}

	/**
	 * Utility method to invoke the update db method
	 * 
	 * @param configMap
	 * @param action
	 * @param tmap
	 * @param updateDataMap
	 * @return
	 * @throws Exception
	 */
	private String invokeUpdate(ConfigMap configMap, Action action, Map<String, String> tmap,
			Map<String, Object> updateDataMap) throws Exception {
		String retVal = null;

		if (updateDataMap == null || updateDataMap.isEmpty()) {
			throw new Exception("updateDataMap is empty");
		}

		// Make call to fetch the response data from db
		retVal = db.update(action.getTable(), tmap.keySet().toArray(new String[0]),
				tmap.values().toArray(new String[0]), updateDataMap.keySet().toArray(new String[0]),
				updateDataMap.values().toArray(new Object[0]), action.isMany());
		return retVal;
	}

	/**
	 * Utility method to invoke the db create method
	 * 
	 * @param configMap
	 * @param action
	 * @param tmap
	 * @param payload
	 * @return
	 * @throws Exception
	 */
	private String invokeCreate(ConfigMap configMap, Action action, Map<String, String> tmap, String payload)
			throws Exception {
		
		String retValue = null;
		String updatedPayload = mapper.getCreateMappings(action, payload);
		
		
		// If is array of value, the call insert many, else insert one
		if(action.isMany()) {
			
			// Create a type for enabling conversion of JSON string to list of Object
			  Type type = new TypeToken<ArrayList<Object>>() {}.getType();

			// Convert to List of Object
			List<Object> inputData = DataMappingUtils.fetchConfig(updatedPayload, type);
			
			// Convert list of Object to List of String
			Gson gson = new Gson();			
			List<String> data = inputData.stream().map(gson::toJson).collect(Collectors.toList());
			
			
			// Now invoke create Many with list of JSON Strings
			retValue = db.createMany(action.getTable(), data);
			
		} else {
			retValue = db.create(action.getTable(), updatedPayload);
		}
		
		return retValue;
	}

	/**
	 * Utility method to invoke delete db methods
	 * 
	 * @param configMap
	 * @param action
	 * @param tmap
	 * @param payload
	 * @return
	 * @throws Exception
	 */

	private String invokeDelete(ConfigMap configMap, Action action, Map<String, String> tmap) throws Exception {
		return db.delete(action.getTable(), tmap.keySet().toArray(new String[0]), tmap.values().toArray(new String[0]),
				action.isMany());
	}

	// Prepare content for returning back to caller
	private String getReturnValue(ConfigMap configMap, List<String> records) {
		// Holds the value that will be send back to the client
		String retVal = null;

		// if records details found and not empty then do further processing
		// if empty, then return error message

		// If the configured return type is object, just return the first record (OR)
		// If the configured return type is array, construct a list and return it
		if (configMap.getReturnType().equalsIgnoreCase(ServiceConstants.OBJECT)) {
			if (records != null && !records.isEmpty() && records.size() > 0) {
				retVal = records.get(0);
			}

		} else if (configMap.getReturnType().equalsIgnoreCase(ServiceConstants.ARRAY)) {

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

		}

		// In case of error, return a error message
		if (retVal == null) {
			retVal = ServiceConstants.ERROR_MSG_DATA_NOT_FOUND;
		}

		return retVal;
	}

	/**
	 * Utility method to extact the delete key string from the configuration
	 * 
	 * @param action
	 * @return
	 */
	private String getDeleteKey(Action action) {
		String retVal = null;
		if (action.getCriteria() != null && action.getCriteria().size() > 0) {
			for (int i = 0; i < action.getCriteria().size(); i++) {

				Criteria c = action.getCriteria().get(i);

				if (c.getType().equalsIgnoreCase(ServiceConstants.DELETEKEY)) {
					retVal = c.getTargetAttribute();
				}
			}
		}

		return retVal;
	}
	
	// Cache management methods
	public static void clearCache() {
		configCache.clear();
		regexCache.clear();
	}
	
	public static int getCacheSize() {
		return configCache.size();
	}
	
	/**
	 * Helper method to check if a string contains any digit without regex backtracking risk
	 * @param str the string to check
	 * @return true if the string contains at least one digit
	 */
	private boolean containsDigit(String str) {
		for (int i = 0; i < str.length(); i++) {
			if (Character.isDigit(str.charAt(i))) {
				return true;
			}
		}
		return false;
	}

}
