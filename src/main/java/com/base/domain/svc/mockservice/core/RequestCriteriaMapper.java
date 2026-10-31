package com.base.domain.svc.mockservice.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.base.domain.svc.mockservice.to.Action;
import com.base.domain.svc.mockservice.to.Criteria;
import com.base.domain.svc.mockservice.utils.ServiceConstants;
import com.base.domain.svc.mockservice.utils.DataMappingUtils;

/**
 * A utility class with all mapping methods TODO: Can be massively optimized
 * 
 * 
 * 
 */
public class RequestCriteriaMapper {

	// Log Handle
	private static Logger log = LogManager.getLogger(RequestCriteriaMapper.class.getName());

	// Create the query mapping which will be used for querying the db
	public Map<String, String> getQueryMappings(Action action, Map<String, String> pathParams,
			Map<String, String[]> queryParams, String payload) {

		// Variable to hold the attributes and values that need to be mapped to db
		// record
		Map<String, String> tmap = new HashMap<>();

		// Loop through the criteria and construct the attributes and values with which
		// the db will be queried
		if (action.getCriteria() != null && action.getCriteria().size() > 0) {
			for (int i = 0; i < action.getCriteria().size(); i++) {

				Criteria c = action.getCriteria().get(i);

				if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.QUERY_PARAMS)
						&& c.getRefString() != null && c.getType() != null && c.getType().equalsIgnoreCase(ServiceConstants.FILTER)) {

					String value = getQueryParamReferenceValue(c.getRefString(), queryParams);
					if (value != null) {
						tmap.put(c.getTargetAttribute(), value);
					} else {
						log.info("### No Mapping query params found for configuration key: " + c.getRefString()
								+ " with configuration : " + action.toString());
					}

				} else if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.BODY)
						&& c.getRefString() != null && c.getType() != null && c.getType().equalsIgnoreCase(ServiceConstants.FILTER)) {

					Gson gson = new Gson();
					Map<String, Object> map = gson.fromJson(payload,
							new TypeToken<Map<String, Object>>() {}.getType());
					String value = (String) DataMappingUtils.getDataFromMap(map, c.getRefString());
					if (value != null) {
						tmap.put(c.getTargetAttribute(), value);
					} else {
						log.info("### No Mapping body params found for configuration key: " + c.getRefString()
								+ " with configuration : " + action.toString());
					}

				} else if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.PATH_PARAM)
						&& c.getRefString() != null && c.getType() != null &&  c.getType().equalsIgnoreCase(ServiceConstants.FILTER)) {

					String value = getPathParamReferenceValue(c.getRefString(), pathParams);
					if (value != null) {
						tmap.put(c.getTargetAttribute(), value);
					} else {
						log.info("### No Mapping path params found for configuration key: " + c.getRefString()
								+ " with configuration : " + action.toString());
					}
				}
			}
		}
		return tmap;
	}

	// Create the query mapping which will be used for querying the db
	public Map<String, Object> getUpdateMappings(Action action, Map<String, String> pathParams,
			Map<String, String[]> queryParams, String payload) throws Exception {

		// Variable to hold the attributes and values that need to be mapped to db
		// record
		Map<String, Object> tmap = new HashMap<>();

		// Loop through the criteria and construct the attributes and values with which
		// the db will be queried
		if (action.getCriteria() != null && action.getCriteria().size() > 0) {
			for (int i = 0; i < action.getCriteria().size(); i++) {

				Criteria c = action.getCriteria().get(i);

				if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.QUERY_PARAMS)
						&& c.getRefString() != null && c.getType() != null &&  c.getType().equalsIgnoreCase(ServiceConstants.DATA)) {

					Object value = getQueryParamReferenceValue(c.getRefString(), queryParams);
					if (value != null) {
						tmap.put(c.getTargetAttribute(), DataMappingUtils.convertDataType(c.getDataType(), value));
					} else {
						log.info("### No Mapping query params found for configuration key: " + c.getRefString()
								+ " with configuration : " + action.toString());
					}

				} else if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.BODY)
						&& c.getRefString() != null && c.getType() != null && c.getType().equalsIgnoreCase(ServiceConstants.DATA)) {

					Gson gson = new Gson();
					Map<String, Object> map = gson.fromJson(payload,
							new TypeToken<Map<String, Object>>() {}.getType());

					if (c.getRefString().trim().isEmpty()) {
						tmap.put(c.getTargetAttribute(), map);

					} else {

						Object value = DataMappingUtils.getDataFromMap(map, c.getRefString());
						if (value != null) {
							tmap.put(c.getTargetAttribute(), DataMappingUtils.convertDataType(c.getDataType(), value));
						} else {
							log.info("### No Mapping body params found for configuration key: " + c.getRefString()
									+ " with configuration : " + action.toString());
						}
					}

				} else if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.PATH_PARAM)
						&& c.getRefString() != null && c.getType() != null &&  c.getType().equalsIgnoreCase(ServiceConstants.DATA)) {

					Object value = getPathParamReferenceValue(c.getRefString(), pathParams);
					if (value != null) {
						tmap.put(c.getTargetAttribute(), DataMappingUtils.convertDataType(c.getDataType(), value));
					} else {
						log.info("### No Mapping path params found for configuration key: " + c.getRefString()
								+ " with configuration : " + action.toString());
					}
				}
			}
		}
		return tmap;
	}

	// Create the query mapping which will be used for creating record in the db
	public String getCreateMappings(Action action, String payload) throws Exception {

		// Variable to hold the data that is needed for create
		String retPayload = null;

		// Loop through the criteria and construct the attributes and values with which
		// the db will be queried
		if (action.getCriteria() != null && action.getCriteria().size() > 0) {

			//
			// We expect only one criteria entry for create, so just take the first one
			//
			Criteria c = action.getCriteria().get(0);

			if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.BODY)) {

				if (c.getRefString() == null || c.getRefString().trim().isEmpty()) {
					retPayload = payload;

				} else {
					Gson gson = new Gson();
					Map<String, Object> map = gson.fromJson(payload,
							new TypeToken<Map<String, Object>>() {}.getType());
					Object value = DataMappingUtils.getDataFromMap(map, c.getRefString());
					if (value != null) {
						retPayload = gson.toJson(value);
					} else {
						log.info("### No Mapping body params found for configuration key: " + c.getRefString()
								+ " with configuration : " + action.toString());
					}
				}

			}

		}
		return retPayload;
	}

	public Map<String, Object> getDeleteFilterMappings(Action action, Map<String, String> pathParams,
			Map<String, String[]> queryParams, String payload) {

		// Variable to hold the attributes and values that need to be mapped to db
		// record
		Map<String, Object> tmap = new HashMap<>();

		// Loop through the criteria and construct the attributes and values with which
		// the db will be queried
		if (action.getCriteria() != null && action.getCriteria().size() > 0) {
			for (int i = 0; i < action.getCriteria().size(); i++) {

				Criteria c = action.getCriteria().get(i);

				if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.QUERY_PARAMS)
						&& c.getRefString() != null && c.getType().equalsIgnoreCase(ServiceConstants.DELETE_FILTER)) {

					String value = getQueryParamReferenceValue(c.getRefString(), queryParams);
					if (value != null) {
						tmap.put(c.getTargetAttribute(), value);
					} else {
						log.info("### No Mapping query params found for configuration key: " + c.getRefString()
								+ " with configuration : " + action.toString());
					}

				} else if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.BODY)
						&& c.getRefString() != null && c.getType().equalsIgnoreCase(ServiceConstants.DELETE_FILTER)) {

					Gson gson = new Gson();
					Map<String, Object> map = gson.fromJson(payload,
							new TypeToken<Map<String, Object>>() {}.getType());
					String value = (String) DataMappingUtils.getDataFromMap(map, c.getRefString());
					if (value != null) {
						tmap.put(c.getTargetAttribute(), value);
					} else {
						log.info("### No Mapping body params found for configuration key: " + c.getRefString()
								+ " with configuration : " + action.toString());
					}

				} else if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.PATH_PARAM)
						&& c.getRefString() != null && c.getType().equalsIgnoreCase(ServiceConstants.DELETE_FILTER)) {

					String value = getPathParamReferenceValue(c.getRefString(), pathParams);
					if (value != null) {
						tmap.put(c.getTargetAttribute(), value);
					} else {
						log.info("### No Mapping path params found for configuration key: " + c.getRefString()
								+ " with configuration : " + action.toString());
					}
				}
			}
		}
		return tmap;
	}

	// Create the query mapping which will be used for querying the db
	public Map<String, String> getUpdateArrayFilterMappings(Action action, Map<String, String> pathParams,
			Map<String, String[]> queryParams, String payload) {

		// Variable to hold the attributes and values that need to be mapped to db
		// record
		Map<String, String> tmap = new HashMap<>();

		// Loop through the criteria and construct the attributes and values with which
		// the db will be queried
		if (action.getCriteria() != null && action.getCriteria().size() > 0) {
			for (int i = 0; i < action.getCriteria().size(); i++) {

				Criteria c = action.getCriteria().get(i);

				if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.QUERY_PARAMS)
						&& c.getRefString() != null && c.getType().equalsIgnoreCase(ServiceConstants.ARRAYFILTER)) {

					String value = getQueryParamReferenceValue(c.getRefString(), queryParams);
					if (value != null) {
						tmap.put(c.getTargetAttribute(), value);
					} else {
						log.info("### No Mapping query params found for configuration key: " + c.getRefString()
								+ " with configuration : " + action.toString());
					}

				} else if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.BODY)
						&& c.getRefString() != null && c.getType().equalsIgnoreCase(ServiceConstants.ARRAYFILTER)) {

					Gson gson = new Gson();
					Map<String, Object> map = gson.fromJson(payload,
							new TypeToken<Map<String, Object>>() {}.getType());
					String value = (String) DataMappingUtils.getDataFromMap(map, c.getRefString());
					if (value != null) {
						tmap.put(c.getTargetAttribute(), value);
					} else {
						log.info("### No Mapping body params found for configuration key: " + c.getRefString()
								+ " with configuration : " + action.toString());
					}

				} else if (c.getSource() != null && c.getSource().equalsIgnoreCase(ServiceConstants.PATH_PARAM)
						&& c.getRefString() != null && c.getType().equalsIgnoreCase(ServiceConstants.ARRAYFILTER)) {

					String value = getPathParamReferenceValue(c.getRefString(), pathParams);
					if (value != null) {
						tmap.put(c.getTargetAttribute(), value);
					} else {
						log.info("### No Mapping path params found for configuration key: " + c.getRefString()
								+ " with configuration : " + action.toString());
					}
				}
			}
		}
		return tmap;
	}

	public List<String> getProjectionFields(Action action) {

		// Return Value, initialize to empty list
		List<String> retVal = new ArrayList<>();

		// Basic sanity check
		// if the is action and if there is criteria, then proceed
		if (action.getCriteria() != null && action.getCriteria().size() > 0) {

			// Loop through the actions and search for type "projections"
			for (int i = 0; i < action.getCriteria().size(); i++) {
				Criteria c = action.getCriteria().get(i);

				// if action of type projection is found and fields to return is not null
				// then copy the entries to return value and break.
				// for one action object, there can be only one criteria of type projections
				if (c.getType() != null && c.getType().equalsIgnoreCase(ServiceConstants.PROJECTIONS)
						&& c.getFieldsToReturn() != null) {
					retVal.addAll(c.getFieldsToReturn());
					break;
				}

			}

		}

		return retVal;
	}

	// Loop through the http request params and extract the value pointed by the
	// reference
	public String getQueryParamReferenceValue(String refString, Map<String, String[]> queryParams) {
		String retVal = null;

		if (queryParams.containsKey(refString)) {
			retVal = queryParams.get(refString)[0];
		}

		return retVal;
	}

	// Loop through the http request params and extract the value pointed by the
	// reference
	public String getPathParamReferenceValue(String refString, Map<String, String> pathParams) {
		String retVal = null;
		
		// Debug logging
		log.info("### Looking for path param: " + refString);
		log.info("### Available path params: " + pathParams.toString());

		if (pathParams.containsKey(refString)) {
			retVal = pathParams.get(refString);
			log.info("### Found path param value: " + retVal);
		} else {
			log.info("### Path param not found: " + refString);
		}

		return retVal;
	}

}
