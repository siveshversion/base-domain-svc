package com.base.domain.svc.mockservice.db;

import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.UpdateResult;
import com.base.domain.svc.mockservice.utils.ServiceConstants;
import com.base.domain.svc.mockservice.utils.MongoClientProvider;
import com.base.domain.svc.mockservice.utils.KafkaEventPublisher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class MongoDataRepository {

	// Log Handle
	private static Logger log = LogManager.getLogger(MongoDataRepository.class.getName());

	public MongoDataRepository() {
	}

	/**
	 * Utility method to get database name. Checks env variable, if available, uses
	 * that else uses default value
	 * 
	 * 
	 * @return
	 */
	private String getDatabaseName() {

		// Get the database name from the system env
		String dbName = System.getenv(ServiceConstants.DATABASE_NAME);

		// if the env does not have database name set, use the default
		if (dbName == null || dbName.isEmpty()) {
			dbName = ServiceConstants.DEFAULT_DB_NAME;
		}

		return dbName;
	}

	/**
	 * Utility method to get mapper collection name. Checks env variable, if
	 * available, uses that else uses default value
	 * 
	 * 
	 * @return
	 */
	private String getMapperCollectionName() {

		// Get the mapper collection name from the system env
		String mapperCollectionName = System.getenv(ServiceConstants.MAPPER_COLLECTION_NAME);

		// if the env does not have mapper collection name set, use the default
		if (mapperCollectionName == null || mapperCollectionName.isEmpty()) {
			mapperCollectionName = ServiceConstants.DEFAULT_MAPPER_COLLECTION_NAME;
		}

		return mapperCollectionName;
	}

	/**
	 * Utlity method to return collection handle for a specified collection The data
	 * database defaults to domain-service-db
	 * 
	 * NOTE: the mongo client cache is initialized with mongo client instance by the
	 * servlet listener during startup
	 * 
	 * @param collectionName
	 * @return
	 */
	public MongoCollection<Document> getCollectionHandle(String collectionName) {
		return MongoClientProvider.getMongoClientInstance().getDatabase(getDatabaseName()).getCollection(collectionName);
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public String createIndexes(String collection, String[] fieldNames, boolean isUnique) throws Exception {

		// Basic error checks
		if (collection == null || collection.trim().isEmpty() || fieldNames == null) {
			throw new Exception("Invalid Input");
		}

		IndexOptions opts = new IndexOptions().unique(isUnique);
		return getCollectionHandle(collection).createIndex(Indexes.ascending(fieldNames), opts);

	}

	public List<String> listIndexes(String collection) throws Exception {

		// Basic error checks
		if (collection == null || collection.trim().isEmpty()) {
			throw new Exception("Invalid Input");
		}

		List<String> retVal = new ArrayList<String>();

		ListIndexesIterable<Document> indexes = getCollectionHandle(collection).listIndexes();

		// Use try-with-resources for index listing
		try (MongoCursor<Document> it = indexes.iterator()) {
			while (it.hasNext()) {
				retVal.add(it.next().toJson());
			}
		}
		// Send back
		return retVal;
	}

	public void dropIndex(String collection, String name) throws Exception {

		// Basic error checks
		if (collection == null || collection.trim().isEmpty()) {
			throw new Exception("Invalid Input");
		}
		getCollectionHandle(collection).dropIndex(name);
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public String createConfigurationIndexes(String[] fieldNames, boolean isUnique) throws Exception {
		return this.createIndexes(getMapperCollectionName(), fieldNames, isUnique);
	}

	public String updateConfigurationById(String id, String inputData) throws Exception {
		return this.updateRecordById(getMapperCollectionName(), id, inputData);
	}

	public List<String> listConfigurationIndexes() throws Exception {
		return this.listIndexes(getMapperCollectionName());
	}

	public void dropConfigurationIndex(String indexName) throws Exception {
		this.dropIndex(getMapperCollectionName(), indexName);
	}

	/**
	 * 
	 * Method to update the records by id. Upsert has issues (check mongodb jira
	 * bug) So instead of updating directly, we are deleting the record and creating
	 * a new record this method replaces the entire record
	 * 
	 * 
	 * @param id
	 * @param inputData
	 * @return
	 * @throws Exception
	 */
	public String updateRecordById(String collection, String id, String inputData) throws Exception {
		String retVal = null;
		// Basic error checks
		if (id == null || inputData == null) {
			throw new Exception("Invalid input - Check the attributes, parameters");
		}

		try {

			long count = this.deleteById(collection, id);
			if (count > 0) {

				retVal = this.create(collection, inputData);
			}

		} catch (Exception e) {
			throw new Exception(e.toString());
		}

		return retVal;
	}

	/**
	 * Method to search and fetch mapper config using regex search inputs string
	 * 
	 * @param url
	 * @return
	 */
	public List<String> fetchConfigurations(String url) throws Exception {

		// Basic error checks
		if (url == null || url.trim().isEmpty()) {
			throw new Exception("Invalid Input");
		}

		List<String> retVal = new ArrayList<String>();
		Bson filter = Filters.regex(ServiceConstants.MAPPER_SEARCH_ATTRIBUTE, url);

		// Use try-with-resources to ensure cursor is properly closed
		// Sort by url length descending so more specific (longer/parameterized) configs come first
		try (MongoCursor<Document> cursor = getCollectionHandle(getMapperCollectionName()).find(filter)
				.sort(new Document(ServiceConstants.MAPPER_SEARCH_ATTRIBUTE, -1))
				.limit(100)
				.iterator()) {

			while (cursor.hasNext()) {
				retVal.add(cursor.next().toJson());
			}
		}

		return retVal;
	}

	public long fetchMatchingConfigurationRecordCount(String[] attributes, String[] parameters) throws Exception {
		return this.readMatchingRecordCount(getMapperCollectionName(), attributes, parameters);

	}

	/**
	 * Method to fetch configuration count. This will be used by UI for pagination
	 * displays
	 * 
	 * @param attributes
	 * @param parameters
	 * @return
	 * @throws Exception
	 */

	public long readMatchingRecordCount(String collection, String[] attributes, String[] parameters) throws Exception {

		// Basic error checks
		if (collection == null || attributes == null || parameters == null) {
			throw new Exception("Invalid Input");
		}

		long matchingCount = -1;

		try {

			// Construct the query
			int length = attributes.length;
			if (length > 0) {
				Document query = new Document(attributes[0], parameters[0]);
				for (int i = 1; i < length; i++) {
					query.append(attributes[i], parameters[i]);
				}
				matchingCount = getCollectionHandle(collection).countDocuments(query);

			} else {
				matchingCount = getCollectionHandle(collection).countDocuments();
			}

		} catch (Exception e) {
			throw new Exception(e.toString());
		}

		return matchingCount;
	}

	/**
	 * Method to fetch mapper config with filter criteria
	 * 
	 * @return
	 */
	public List<String> fetchConfigurations(String[] attributes, Object[] parameters) throws Exception {

		return readAll(getMapperCollectionName(), attributes, parameters, null);
	}

	/**
	 * Method to fetch mapper config with filter criteria that support pagination
	 * 
	 * @param url
	 * @return
	 */

	public List<String> fetchConfigurationsWithPagination(String[] attributes, String[] parameters, int pageNum,
			int pageSize) throws Exception {
		return this.readRecordsWithPagination(getMapperCollectionName(), attributes, parameters, pageNum, pageSize);
	}

	public List<String> readRecordsWithPagination(String collection, String[] attributes, String[] parameters,
			int pageNum, int pageSize) throws Exception {

		// Basic error checks
		if (collection == null || attributes == null || parameters == null || pageNum < 0 || pageSize <= 0) {
			throw new Exception("Invalid Input");
		}

		List<String> retVal = new ArrayList<String>();
		FindIterable<Document> result = null;
		try {

			// Construct the query
			int length = attributes.length;
			if (length > 0) {
				Document query = new Document(attributes[0], parameters[0]);

				for (int i = 1; i < length; i++) {
					query.append(attributes[i], parameters[i]);
				}
				result = getCollectionHandle(collection).find(query).skip(pageSize * (pageNum - 1)).limit(pageSize);
			} else {
				result = getCollectionHandle(collection).find().skip(pageSize * (pageNum - 1)).limit(pageSize);
			}

		} catch (Exception e) {
			throw new Exception(e.toString());
		}

		// Use try-with-resources for pagination cursor
		try (MongoCursor<Document> cursor = result.iterator()) {
			while (cursor.hasNext()) {
				retVal.add(cursor.next().toJson());
			}
		}

		return retVal;
	}

	/**
	 * Method to delete configuration based on id
	 * 
	 * @param id
	 * @return
	 * @throws Exception
	 */

	public long deleteConfigurationById(String id) throws Exception {
		return deleteById(getMapperCollectionName(), id);
	}

	public String deleteConfiguration(String[] attributes, String[] parameters) throws Exception {
		return this.delete(getMapperCollectionName(), attributes, parameters, true);
	}

	/**
	 * Method to insert one single configuration data
	 * 
	 * @param inputJSON
	 * @return
	 */
	public String createConfiguration(String inputJSON) throws Exception {

		// Basic error checks
		if (inputJSON == null) {
			throw new Exception("Invalid Input");
		}

		String retVal = null;

		try {
			Document data = new Document(Document.parse(inputJSON));
			String collection = getMapperCollectionName();
			org.bson.BsonValue insertedId = getCollectionHandle(collection).insertOne(data).getInsertedId();
			retVal = insertedId.toString();
			KafkaEventPublisher.publish(collection, "create", toRecordId(insertedId));
		} catch (MongoWriteException e) {

			// If duplicate error, send error message to users, else, throw exception
			if (e.getCode() == 11000) {
				retVal = "ERROR : Duplicate Record";
			} else {
				throw new Exception(e.getCause());
			}

		}

		return retVal;
	}

	/**
	 * Method to insert one multiple configuration data
	 * 
	 * @param inputJSON
	 * @return
	 */
	public String createConfigurations(List<String> inputJSONs) throws Exception {

		// Basic error checks
		if (inputJSONs == null || inputJSONs.isEmpty()) {
			throw new Exception("Invalid Input");
		}

		List<Document> data = new ArrayList<>();
		for (int i = 0; i < inputJSONs.size(); i++) {
			data.add(new Document(Document.parse(inputJSONs.get(i))));
		}
		InsertManyResult result = getCollectionHandle(getMapperCollectionName()).insertMany(data);
		String collection = getMapperCollectionName();
		for (org.bson.BsonValue id : result.getInsertedIds().values()) {
			KafkaEventPublisher.publish(collection, "create", toRecordId(id));
		}
		return result.getInsertedIds().toString();
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Create / Insert operations
	 * 
	 * @param collection
	 * @param inputJSON
	 * @return
	 */

	public String create(String collection, String inputJSON) throws Exception {

		// Basic error checks
		if (inputJSON == null || inputJSON.trim().isEmpty()) {
			throw new Exception("Invalid Input");
		}

		Document data = new Document(Document.parse(inputJSON));
		org.bson.BsonValue insertedId = getCollectionHandle(collection).insertOne(data).getInsertedId();
		KafkaEventPublisher.publish(collection, "create", toRecordId(insertedId));

		return data.toJson();
	}

	/**
	 * Create / Insert operations
	 * 
	 * @param collection
	 * @param inputJSON
	 * @return
	 */

	public String createMany(String collection, List<String> inputJSONs) throws Exception {

		// Basic error checks
		if (inputJSONs == null || inputJSONs.isEmpty()) {
			throw new Exception("Invalid Input");
		}

		// Parse the list of string to list of document
		List<Document> data = inputJSONs.stream().map(Document::parse).collect(Collectors.toList());

		// Call insert Many
		InsertManyResult result = getCollectionHandle(collection).insertMany(data);
		for (org.bson.BsonValue id : result.getInsertedIds().values()) {
			KafkaEventPublisher.publish(collection, "create", toRecordId(id));
		}

		return result.toString();
	}

	/**
	 * Read method, take bunch of filters as input and apply the same for read
	 * operations
	 * 
	 * 
	 * @param collection
	 * @param attributes
	 * @param parameters
	 * @return
	 * @throws Exception
	 */
	public List<String> readAll(String collection, String[] attributes, Object[] parameters,
			List<String> fieldsToReturn) throws Exception {
		// Basic error checks
		if (attributes == null || parameters == null) {
			throw new Exception("Invalid input - Check the attributes, parameters");
		}

		// Construct the query
		Document query = null;
		int queryLength = attributes.length;

		if (queryLength > 0) {
			query = new Document(attributes[0], parameters[0]);
		}
		try {
			for (int i = 1; i < queryLength; i++) {
				if (query != null)
					query.append(attributes[i], parameters[i]);
			}
		} catch (Exception e) {
			throw new Exception(e.toString());
		}

		Bson projection = null;
		if (fieldsToReturn != null && !fieldsToReturn.isEmpty()) {

			projection = Projections.fields(Projections.include(fieldsToReturn));
		} else {
			projection = Projections.fields(Projections.include(new ArrayList<>()));
		}

		List<String> retVal = new ArrayList<String>();

		// Use try-with-resources for proper cursor management
		FindIterable<Document> findIterable = (query != null) ? getCollectionHandle(collection).find(query)
				: getCollectionHandle(collection).find();

		try (MongoCursor<Document> cursor = findIterable.projection(projection)
				.limit(ServiceConstants.MAX_RECORDS_RETURNED).iterator()) {

			while (cursor.hasNext()) {
				retVal.add(cursor.next().toJson());
			}
		} catch (Exception e) {
			throw new Exception(e.toString());
		}

		// Send back
		return retVal;
	}

	public String update(String collections, String[] dataAttributes, String[] dataParameters, String[] updateKeys,
			Object[] updateDatas, boolean isMany) throws Exception {

		String retVal = "{}";

		// Log the filter criteria for debugging
		log.info("### Update operation - Collection: " + collections);
		log.info("### Filter attributes: " + java.util.Arrays.toString(dataAttributes));
		log.info("### Filter parameters: " + java.util.Arrays.toString(dataParameters));
		log.info("### Update keys: " + java.util.Arrays.toString(updateKeys));
		log.info("### Update values: " + java.util.Arrays.toString(updateDatas));

		// Collect the filter criterias, which will used later in the query
		int length = dataAttributes.length;
		Bson filters[] = new Bson[length];
		for (int i = 0; i < length; i++) {
			filters[i] = eq(dataAttributes[i], dataParameters[i]);
		}
		Bson filter = Filters.and(filters);
		log.info("### MongoDB filter: " + filter.toString());

		// Check if document exists before update
		long count = getCollectionHandle(collections).countDocuments(filter);
		log.info("### Documents matching filter: " + count);
		
		// Debug: Check total documents in collection
		long totalDocs = getCollectionHandle(collections).countDocuments();
		log.info("### Total documents in collection '" + collections + "': " + totalDocs);
		
		// Debug: Show first few documents in collection
		try (MongoCursor<Document> cursor = getCollectionHandle(collections).find().limit(5).iterator()) {
			int docCount = 0;
			while (cursor.hasNext()) {
				Document doc = cursor.next();
				log.info("### Sample document " + (++docCount) + ": " + doc.toJson());
			}
		}

		// Collect all the updates, which will later used in the query
		// NOTE: Array updates cannot be combined with non-array updates
		int updateLength = updateKeys.length;
		List<Bson> updatesList = new ArrayList<>();
		for (int i = 0; i < updateLength; i++) {
			updatesList.add(Updates.set(updateKeys[i], updateDatas[i]));
		}
		Bson updates = Updates.combine(updatesList);

		// if config say update one, then do so, else update many
		if (!isMany) {
			// First, let's see the document before update
			Document beforeUpdate = getCollectionHandle(collections).find(filter).first();
			log.info("### Document before update: " + (beforeUpdate != null ? beforeUpdate.toJson() : "null"));
			
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
			Document res = getCollectionHandle(collections).findOneAndUpdate(filter, updates, options);
			
			log.info("### Document after update: " + (res != null ? res.toJson() : "null"));
			
			if (res != null) {
				retVal = res.toJson();
				KafkaEventPublisher.publish(collections, "update", toRecordId(res));
			} else {
				retVal = "{\"error\": \"No document found to update\"}";
			}

		} else {
			UpdateResult res = getCollectionHandle(collections).updateMany(filter, updates);
			log.info("### UpdateMany result: " + res.toString());
			retVal = res.toString();
			if (res.getModifiedCount() > 0) {
				KafkaEventPublisher.publish(collections, "update", null);
			}
		}

		return retVal;
	}

	public String updateArray(String collections, String[] dataAttributes, String[] dataParameters,
			String[] arrayFilterAttributes, String[] arrayFilterParameters, String updateKey, Object updateData,
			String crudType, boolean isMany) throws Exception {

		String retVal = "{}";
		
		// Add detailed logging
		log.info("### updateArray called with:");
		log.info("### Collection: " + collections);
		log.info("### Data attributes: " + Arrays.toString(dataAttributes));
		log.info("### Data parameters: " + Arrays.toString(dataParameters));
		log.info("### Array filter attributes: " + Arrays.toString(arrayFilterAttributes));
		log.info("### Array filter parameters: " + Arrays.toString(arrayFilterParameters));
		log.info("### Update key: " + updateKey);
		log.info("### Update data: " + updateData);
		log.info("### CRUD type: " + crudType);
		log.info("### Is many: " + isMany);

		int length = dataAttributes.length;
		Bson filter;
		
		if (length == 0) {
			// If no filter attributes, create an empty filter that matches all documents
			filter = new Document();
			log.info("### No filter attributes provided, using empty filter");
		} else {
			Bson filters[] = new Bson[length];
			for (int i = 0; i < length; i++) {
				filters[i] = eq(dataAttributes[i], dataParameters[i]);
			}
			filter = Filters.and(filters);
		}
		log.info("### Main filter: " + filter.toBsonDocument());

		int arrayFilterLength = arrayFilterAttributes.length;

		Bson arrayfilters[] = new Bson[arrayFilterLength];
		for (int i = 0; i < arrayFilterLength; i++) {
			arrayfilters[i] = eq(arrayFilterAttributes[i], arrayFilterParameters[i]);
		}
		
		log.info("### Array filters length: " + arrayFilterLength);
		for (int i = 0; i < arrayFilterLength; i++) {
			log.info("### Array filter " + i + ": " + arrayfilters[i].toBsonDocument());
		}

		Bson update = null;

		if (crudType.equalsIgnoreCase(ServiceConstants.UPDATE_ARRAY_ADD_ELEMENT)) {
			// Check for duplicate array element key before pushing
			if (updateData instanceof Document) {
				Document newElement = (Document) updateData;
				String arrayField = updateKey;
				for (String key : newElement.keySet()) {
					Object val = newElement.get(key);
					Bson dupCheck = Filters.and(filter, Filters.eq(arrayField + "." + key, val));
					if (getCollectionHandle(collections).countDocuments(dupCheck) > 0) {
						return "{\"error\": \"Duplicate value for '" + key + "': " + val + "\"}";
					}
					break; // only check the first field (the ID field)
				}
			}
			update = Updates.push(updateKey, updateData);
		} else {
			update = Updates.set(updateKey, updateData);
		}
		
		log.info("### Update operation: " + update.toBsonDocument());

		// Check if document exists before update
		long count = getCollectionHandle(collections).countDocuments(filter);
		log.info("### Documents matching main filter: " + count);

		// if config say update one, then do so, else update many
		if (!isMany) {
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
			
			// Only add array filters if they exist
			if (arrayFilterLength > 0) {
				options.arrayFilters(Arrays.asList(arrayfilters));
			}
			
			Document res = getCollectionHandle(collections).findOneAndUpdate(filter, update, options);
			log.info("### Update result: " + (res != null ? res.toJson() : "null"));
			if (res != null) {
				retVal = res.toJson();
				KafkaEventPublisher.publish(collections, crudType, toRecordId(res));
			} else {
				retVal = "{\"error\": \"No document found to update\"}";
			}

		} else {
			UpdateOptions options = new UpdateOptions();
			
			// Only add array filters if they exist
			if (arrayFilterLength > 0) {
				options.arrayFilters(Arrays.asList(arrayfilters));
			}
			
			UpdateResult res = getCollectionHandle(collections).updateMany(filter, update, options);
			log.info("### UpdateMany result: " + res.toString());
			retVal = res.toString();
			if (res.getModifiedCount() > 0) {
				KafkaEventPublisher.publish(collections, crudType, null);
			}

		}

		return retVal;
	}

	public String delete(String collections, String[] dataAttributes, String[] dataParameters, boolean isMany)
			throws Exception {

		String retVal = "{}";

		int length = dataAttributes.length;
		Bson filters[] = new Bson[length];
		for (int i = 0; i < length; i++) {
			filters[i] = eq(dataAttributes[i], dataParameters[i]);
		}

		Bson filter = Filters.and(filters);

		// if config say update one, then do so, else update many
		if (!isMany) {
			Document res = getCollectionHandle(collections).findOneAndDelete(filter);
			if (res != null) {
				retVal = res.toJson();
				KafkaEventPublisher.publish(collections, "delete", toRecordId(res));
			} else {
				retVal = "{\"error\": \"No document found to delete\"}";
			}

		} else {
			DeleteResult res = getCollectionHandle(collections).deleteMany(filter);
			retVal = res.toString();
			if (res.getDeletedCount() > 0) {
				KafkaEventPublisher.publish(collections, "delete", null);
			}
		}

		return retVal;
	}

	public String deleteArrayElement(String collections, String[] dataAttributes, String[] dataParameters,
			String[] arrayFilterAttributes, String[] arrayFilterParameters, String[] deleteFilterAttributes,
			String[] deleteFilterparams, String deleteKey, boolean isMany) throws Exception {

		String retVal = "{}";

		int length = dataAttributes.length;
		Bson filters[] = new Bson[length];
		for (int i = 0; i < length; i++) {
			filters[i] = eq(dataAttributes[i], dataParameters[i]);
		}
		Bson filter = Filters.and(filters);

		int arrayFilterLength = arrayFilterAttributes.length;
		Bson arrayfilters[] = new Bson[arrayFilterLength];
		for (int i = 0; i < arrayFilterLength; i++) {
			arrayfilters[i] = eq(arrayFilterAttributes[i], arrayFilterParameters[i]);
		}

		int delFilterLength = deleteFilterAttributes.length;
		Bson delFilters[] = new Bson[delFilterLength];
		for (int i = 0; i < delFilterLength; i++) {
			delFilters[i] = eq(deleteFilterAttributes[i], deleteFilterparams[i]);
		}

		Bson delFilter = Filters.and(delFilters);

		Bson update = Updates.pull(deleteKey, delFilter);

		// if config say update one, then do so, else update many
		if (!isMany) {
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
					.arrayFilters(Arrays.asList(arrayfilters));

			Document res = getCollectionHandle(collections).findOneAndUpdate(filter, update, options);
			if (res != null) {
				retVal = res.toJson();
				KafkaEventPublisher.publish(collections, "update-array-delete-element", toRecordId(res));
			} else {
				retVal = "{\"error\": \"No document found to update\"}";
			}

		} else {
			UpdateOptions options = new UpdateOptions().arrayFilters(Arrays.asList(arrayfilters));
			UpdateResult res = getCollectionHandle(collections).updateMany(filter, update, options);
			retVal = res.toString();
			if (res.getModifiedCount() > 0) {
				KafkaEventPublisher.publish(collections, "update-array-delete-element", null);
			}

		}

		return retVal;
	}

	public long deleteById(String collection, String id) throws Exception {

		// Basic error checks
		if (id == null) {
			throw new Exception("Invalid input - Check the attributes, parameters");
		}

		BasicDBObject query = new BasicDBObject();
		query.put("_id", new ObjectId(id));

		long retVal = 0;

		try {
			retVal = getCollectionHandle(collection).deleteMany(query).getDeletedCount();
			if (retVal > 0) {
				KafkaEventPublisher.publish(collection, "delete", id);
			}
		} catch (Exception e) {
			throw new Exception(e.toString());
		}
		return retVal;
	}

	public void dropCollection(String collection) throws Exception {
		// Basic error checks
		if (collection == null || collection.trim().isEmpty()) {
			throw new Exception("Invalid input - Check the attributes, parameters");
		}
		getCollectionHandle(collection).drop();
		KafkaEventPublisher.publish(collection, "drop-collection", null);
	}

	public List<String> listCollections() throws Exception {

		List<String> retVal = new ArrayList<String>();

		MongoIterable<String> collections = MongoClientProvider.getMongoClientInstance().getDatabase(getDatabaseName())
				.listCollectionNames();
		// Use try-with-resources for collection listing
		try (MongoCursor<String> it = collections.iterator()) {
			while (it.hasNext()) {
				retVal.add(it.next());
			}
		}
		// Send back
		return retVal;

	}

	/**
	 * Atomically generates the next ID in the form: desiredPrefix + zero-padded
	 * number.
	 *
	 * Isolation is per (collection, idField, desiredPrefix) via a composite counter
	 * key.
	 *
	 * Example: desiredPrefix="SR", minNumberWidth=4 -> "SR0001", "SR0002", ...
	 *
	 * @param collection     target collection name (namespaces the counter)
	 * @param idField        target id field (namespaces the counter)
	 * @param desiredPrefix  e.g., "SR"
	 * @param minNumberWidth minimum zero-padding for the numeric part (>=1)
	 * @return next ID (e.g., "SR0001")
	 */
	public String generateDynamicAutoGenIDAtomic(String collection, String idField, String desiredPrefix,
			int minNumberWidth) throws Exception {
		if (collection == null || collection.trim().isEmpty() || idField == null || idField.trim().isEmpty()
				|| desiredPrefix == null) {
			throw new Exception("Invalid input - collection/idField/prefix required");
		}
		if (minNumberWidth < 1)
			minNumberWidth = 1;

		// Use a composite key to isolate counters per (collection, idField, prefix)
		final String counterKey = collection + ":" + idField + ":" + desiredPrefix;

		// Upsert a counter doc and atomically increment "seq"
		FindOneAndUpdateOptions opts = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true);

		// "counters" is a small, dedicated collection for sequence documents
		MongoCollection<Document> counters = getCollectionHandle("counters");

		Document next = counters.findOneAndUpdate(Filters.eq("_id", counterKey), Updates.inc("seq", 1), opts);

		if (next == null || !next.containsKey("seq")) {
			throw new IllegalStateException("Atomic counter missing/invalid for key: " + counterKey);
		}

		int seq = next.getInteger("seq", 0);
		// Grow width naturally with the sequence; always at least minNumberWidth
		int width = Math.max(minNumberWidth, String.valueOf(seq).length());
		String numberFormatted = String.format("%0" + width + "d", seq);

		return desiredPrefix + numberFormatted;

	}

	private static String toRecordId(Document document) {
		Object id = document.get("_id");
		return id == null ? null : id.toString();
	}

	private static String toRecordId(org.bson.BsonValue id) {
		if (id == null) {
			return null;
		}
		return id.isObjectId() ? id.asObjectId().getValue().toHexString() : id.toString();
	}
}
