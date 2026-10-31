package com.base.domain.svc.mockservice.utils;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MongoClientProvider implements Serializable {

	private static final long serialVersionUID = 6622297509790643594L;

	// Log Handle
	private static Logger log = LogManager.getLogger(MongoClientProvider.class.getName());

	// Holds the single instance of Mongo client
	// This instance will use by all within this application
	private static volatile MongoClient mongoClient = null;
	private static final Object lock = new Object();

	// Returns this static instance of the mongoclient
	public static MongoClient getMongoClientInstance() {
		if (mongoClient == null) {
			synchronized (lock) {
				if (mongoClient == null) {
					try {
						init();
					} catch (Exception e) {
						log.error("Failed to initialize MongoDB client", e);
						throw new RuntimeException("MongoDB initialization failed", e);
					}
				}
			}
		}
		return mongoClient;
	}

	/**
	 * Method to initialize the mongo db connection with optimized settings
	 * 
	 * @throws Exception
	 */
	public static void init() throws Exception {
		
		String connectionURL = null;		
		
		// Get the mongodb connection URL from the system env
		String connectionDetails = System.getenv(ServiceConstants.MONGODB_CONNECTION_URL);

		// debug statements
		log.info("#### connectionDetails URL from ENV : " + connectionDetails);

		//
		// if the env have mongodb connection URL set, use that else use defaults
		//
		if (connectionDetails != null && !connectionDetails.isEmpty()) {
			connectionURL = "mongodb://" + connectionDetails;
		} else {
			connectionURL = ServiceConstants.DEFAULT_MONGODB_CONNECTION_URL;
		}
		
		// debug statements
		log.info("#### Optimized Connection URL : " + connectionURL);

		// Create optimized client with connection pooling
		MongoClientSettings settings = MongoClientSettings.builder()
			.applyConnectionString(new ConnectionString(connectionURL))
			.applyToConnectionPoolSettings(builder -> 
				builder.maxSize(20)
					   .minSize(5)
					   .maxWaitTime(2, TimeUnit.SECONDS)
					   .maxConnectionIdleTime(30, TimeUnit.SECONDS)
					   .maxConnectionLifeTime(60, TimeUnit.SECONDS))
			.applyToSocketSettings(builder ->
				builder.connectTimeout(5, TimeUnit.SECONDS)
					   .readTimeout(10, TimeUnit.SECONDS))
			.build();

		mongoClient = MongoClients.create(settings);
	}

	/**
	 * Method to close the Mongo client connection
	 */
	public static void close() {
		if (mongoClient != null) {
			synchronized (lock) {
				if (mongoClient != null) {
					mongoClient.close();
					mongoClient = null;
				}
			}
		}
	}

}
