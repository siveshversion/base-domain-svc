package com.base.domain.svc.mockservice.utils;

public class ServiceConstants {

	public static final String UPDATE = "update";
	public static final String DELETE = "delete";
	public static final String UPDATE_ARRAY_ELEMENT = "update-array-element";
	public static final String UPDATE_ARRAY_ADD_ELEMENT = "update-array-add-element";
	public static final String UPDATE_ARRAY_DELETE_ELEMENT = "update-array-delete-element";
	public static final String CREATE = "create";
	public static final String READ = "read";
	public static final String FIXED_RETURN = "fixed-return";
	public static final String PROJECTIONS = "projections";
	
	public static final String ARRAY = "array";
	public static final String OBJECT = "object";

	public static final String BODY = "body";
	public static final String QUERY_PARAMS = "query-param";
	public static final String PATH_PARAM = "path-param";


	public static final String FORWARD_SLASH = "/";
	public static final String CLOSE_BRACKET_PATTERN = "}";
	public static final String OPEN_BRACKET_PATTERN = "{";
	public static final String PREFIX_PATTERN = "{{";
	public static final String BACKSLASH_PATTERN = "\\/";
	public static final String REGIX_PATTERN = "[a-zA-Z0-9_\\-]*";
	
	public static final String MAPPER_SEARCH_ATTRIBUTE = "url";
	public final static int MAX_RECORDS_RETURNED = 400;

	public static final String STRING = "string";
	public static final String FLOAT = "float";
	public static final String DOUBLE = "double";
	public static final String INT = "int";
	public static final String BOOL = "boolean";
	
	
	public static final String ARRAYFILTER = "arrayfilter";
	public static final String DELETE_FILTER = "deleteFilter";
	public static final String DATA = "data";
	public static final String FILTER = "filter";
	
	public static final String DELETEKEY = "deletekey";
	
	public static final String ERROR_MSG_CHECK_LOGS = "{\"error\" :\" Error.  Check server logs\"} ";
	public static final String ERROR_MSG_DATA_NOT_FOUND = " { \"error\" : \" Data not found \"}";
	
	// Holds the default connection url
	public static final String DEFAULT_MONGODB_CONNECTION_URL = "mongodb://localhost:27017/";
	
	// The key name which will used to set the connection url in the system env
	// This key is automatically set by the infra team with the given key string
	public static final String MONGODB_CONNECTION_URL = "DOMAIN_SVC_MONGODB_HOST_WITH_CREDENTIALS";
	
	// The key name which will used to set the database name in the system env
	public static final String DATABASE_NAME = "DOMAIN_SVC_MONGODB_DATABASE";
	public static final String DEFAULT_DB_NAME = "domain-service-db";
	
	// The key name which will used to set the mapper collection name in the system env
	public static final String MAPPER_COLLECTION_NAME = "DOMAIN_SVC_MONGODB_CONFIG_COLLECTION";
	public static final String DEFAULT_MAPPER_COLLECTION_NAME = "endpoint-configurations";

	public static final String KAFKA_BOOTSTRAP_SERVERS = "DOMAIN_SVC_KAFKA_BOOTSTRAP_SERVERS";
	public static final String KAFKA_TOPIC_PREFIX = "DOMAIN_SVC_KAFKA_TOPIC_PREFIX";
}
