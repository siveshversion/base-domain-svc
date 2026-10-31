package com.base.domain.svc.mockservice.utils;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * <ul>
 * <li>Mongo DB documentation suggest that we create just one "MongoClient" per
 * application instance. <a>
 * https://www.mongodb.com/docs/manual/administration/connection-pool-overview</a>
 * </li>
 * <li>We achieve that through this listener class that implements
 * ServletContextListener</li>
 * <li>The mongo client instance are created when the application starts</li>
 * <li>The mongo client instance is destroyed when the application closed</li>
 * <li>We achieve this by overriding "contextInitialized" and "contextDestroyed"
 * methods</li>
 * <li>The web.xml entry ensures that this listner is added to the chain</li>
 * </ul>
 * </p>
 * 
 * 
 * @author 117808
 *
 */

public class MongoClientLifecycleListener implements ServletContextListener {

	// Log Handle
	private static Logger log = LogManager.getLogger(MongoClientLifecycleListener.class.getName());

	// Instance of class that manages the mongodb connection instances
	MongoClientProvider handle;

	/**
	 * Override the context initializer method to initialize the "MongoClientProvider"
	 * "MongoClientProvider" manages the instance of Mongoclient
	 */
	@Override
	public void contextInitialized(ServletContextEvent servletContextEvent) {
		log.info("###  About to Initialize MongoClient Instance ###");

		// Create a new instance and initialize the mongodb connection
		// Abort in case of failure
		try {
			handle = new MongoClientProvider();
			MongoClientProvider.init();
		} catch (Exception e) {
			log.error("###  MongoInitializationServletListener Initialized FAILED ###");
			log.error(e.getMessage());
		}

	}

	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		log.info("### About to close Mongo Client intance ###");

		KafkaEventPublisher.close();

		// Close the mongodb instance and nullify the variable
		// TODO: add basic check to see if handle is not null
		if (handle != null) {
			handle.close();
		}
		handle = null;
	}

}
