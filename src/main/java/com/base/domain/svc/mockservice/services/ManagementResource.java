package com.base.domain.svc.mockservice.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import com.base.domain.svc.mockservice.core.ConfiguredRequestProcessor;
import com.base.domain.svc.mockservice.utils.ServiceConstants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Path("/mock")
public class ManagementResource {

	// Log Handle
	private static Logger logger = LogManager.getLogger(ConfiguredRequestProcessor.class.getName());

	ManagementOperations handle = new ManagementOperations();

	// Resource tracking
	private static final AtomicLong minMemoryMB = new AtomicLong(Long.MAX_VALUE);
	private static final AtomicLong maxMemoryMB = new AtomicLong(0);
	private static final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

	/////////////////////////////////////////////////////////////////////////////////////////////////////

	@GET
	@Path("/env-info")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response getEnvironmentInfo() {
		List<Map<String, String>> environment = new ArrayList<>();
		addEnvironmentValue(environment, ServiceConstants.MONGODB_CONNECTION_URL,
				maskMongoCredentials(environmentValue(ServiceConstants.MONGODB_CONNECTION_URL, "localhost:27017")));
		addEnvironmentValue(environment, ServiceConstants.DATABASE_NAME,
				environmentValue(ServiceConstants.DATABASE_NAME, ServiceConstants.DEFAULT_DB_NAME));
		addEnvironmentValue(environment, ServiceConstants.MAPPER_COLLECTION_NAME,
				environmentValue(ServiceConstants.MAPPER_COLLECTION_NAME,
						ServiceConstants.DEFAULT_MAPPER_COLLECTION_NAME));
		addEnvironmentValue(environment, ServiceConstants.KAFKA_BOOTSTRAP_SERVERS,
				environmentValue(ServiceConstants.KAFKA_BOOTSTRAP_SERVERS, "not set"));
		addEnvironmentValue(environment, ServiceConstants.KAFKA_TOPIC_PREFIX,
				environmentValue(ServiceConstants.KAFKA_TOPIC_PREFIX, "not set"));
		return Response.ok(environment).build();
	}

	private void addEnvironmentValue(List<Map<String, String>> environment, String key, String value) {
		Map<String, String> entry = new LinkedHashMap<>();
		entry.put("key", key);
		entry.put("value", value);
		environment.add(entry);
	}

	private String environmentValue(String key, String defaultValue) {
		String value = System.getenv(key);
		return value == null || value.isEmpty() ? defaultValue : value;
	}

	private String maskMongoCredentials(String connectionDetails) {
		int firstAt = connectionDetails.indexOf('@');
		if (firstAt < 0) {
			return connectionDetails;
		}
		if (firstAt != connectionDetails.lastIndexOf('@')) {
			return "configured (credentials redacted)";
		}
		return "***@" + connectionDetails.substring(firstAt + 1);
	}

	@POST
	@Path("/configurations")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response addConfiguration(@Context HttpHeaders headers, @Context HttpServletRequest request,
			@Context HttpServletResponse response) {

		Response retVal = null;

		StringBuilder buffer = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));) {

			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line);
			}
			logger.info("#####-#### mockservice service - create - input data " + buffer.toString());

			retVal = Response.status(200).entity(handle.addConfiguration(buffer.toString())).build();
		} catch (Exception e) {

			logger.error("#####-#### Add Configuration Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@GET
	@Path("/configurations/paging")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response fetchCongiurationWithPagination(@Context UriInfo uriInfo, @QueryParam("pagenum") int pageNum,
			@QueryParam("pageSize") int pageSize) throws Exception {

		MultivaluedMap<String, String> filters = uriInfo.getQueryParameters();
		Response retVal = null;

		try {
			retVal = Response.status(200).entity(handle.fetchMatchingRecordsWithPagination(filters, pageNum, pageSize))
					.build();

		} catch (Exception e) {
			logger.error("#####-#### Fetch Configuration with pagination Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@GET
	@Path("/configurations")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response fetchCongiurations(@Context UriInfo uriInfo) throws Exception {

		MultivaluedMap<String, String> filters = uriInfo.getQueryParameters();
		Response retVal = null;

		try {
			retVal = Response.status(200).entity(handle.fetchMatchingRecords(filters)).build();
		} catch (Exception e) {
			logger.error("#####-#### Fetch Configuration with pagination Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@GET
	@Path("/configurations/fetch-record-count")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response fetchMatchingPageCount(@Context UriInfo uriInfo) throws Exception {
		Response retVal = null;
		MultivaluedMap<String, String> filters = uriInfo.getQueryParameters();
		try {
			retVal = Response.status(200).entity(handle.fetchMatchingConfigurationRecordCount(filters)).build();

		} catch (Exception e) {
			logger.error("#####-#### Fetch Configuration Count Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@PUT
	@Path("/configurations/{objectid}")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response updateConfiguration(@Context HttpServletRequest request, @PathParam("objectid") String id) {

		Response retVal = null;

		StringBuilder buffer = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));) {

			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line);
			}
			logger.info("#####-#### mockservice service - update - input data " + buffer.toString());

			retVal = Response.status(200).entity(handle.updateConfiguration(id, buffer.toString())).build();
		} catch (Exception e) {

			logger.error("#####-#### Update Configuration Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@DELETE
	@Path("/configurations")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response deleteConfiguration(@QueryParam("url") String url, @QueryParam("verb") String verb)
			throws Exception {
		Response retVal = null;
		try {

			if (url == null || verb == null) {
				throw new Exception("Invalid parameters");
			}

			Map<String, String> param = new HashMap<>();
			param.put(ServiceConstants.MAPPER_SEARCH_ATTRIBUTE, url);
			param.put("verb", verb);

			retVal = Response.status(200).entity(handle.deleteConfiguration(param)).build();

		} catch (Exception e) {
			logger.error("#####-#### Delete Configuration Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@DELETE
	@Path("/configurations/{objectid}")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response deleteConfigurationWithId(@PathParam("objectid") String id) throws Exception {
		Response retVal = null;
		try {

			retVal = Response.status(200).entity(handle.deleteConfigurationwithId(id)).build();

		} catch (Exception e) {
			logger.error("#####-#### Delete Configuration by Id Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@POST
	@Path("/configurations/indexes")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response addConfigurationIndexes(@Context HttpHeaders headers, @Context HttpServletRequest request,
			@Context HttpServletResponse response, @QueryParam("isUnique") boolean isUnique) {

		Response retVal = null;

		StringBuilder buffer = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));) {

			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line);
			}
			logger.info("#####-#### mockservice service - create indexes- input data " + buffer.toString());

			retVal = Response.status(200).entity(handle.createConfigurationIndexes(buffer.toString(), isUnique))
					.build();
		} catch (Exception e) {

			logger.error("#####-#### Add Configuration indexes Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@GET
	@Path("/configurations/indexes")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response getConfigurationIndexes() throws Exception {
		Response retVal = null;
		try {

			retVal = Response.status(200).entity(handle.listConfigurationIndexes()).build();

		} catch (Exception e) {
			logger.error("#####-#### List Configuration Indexes Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@DELETE
	@Path("/configurations/indexes/{indexName}")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response deleteConfigurationIndex(@PathParam("indexName") String indexName) throws Exception {
		Response retVal = null;
		try {

			handle.dropConfigurationIndexes(indexName);
			retVal = Response.status(200).build();

		} catch (Exception e) {
			logger.error("#####-#### List Configuration Indexes Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////

	@POST
	@Path("/data-records")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response addData(@Context HttpHeaders headers, @Context HttpServletRequest request,
			@Context HttpServletResponse response, @QueryParam("table") String collection) {

		Response retVal = null;

		StringBuilder buffer = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));) {

			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line);
			}
			logger.info("#####-#### mockservice service - Add Data - input data " + buffer.toString());

			retVal = Response.status(200).entity(handle.addData(collection, buffer.toString())).build();
		} catch (Exception e) {

			logger.error("#####-#### Add data Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@GET
	@Path("/data-records/paging")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response readRecordsWithPagination(@Context UriInfo uriInfo, @QueryParam("pagenum") int pageNum,
			@QueryParam("pageSize") int pageSize, @QueryParam("table") String collection) {

		MultivaluedMap<String, String> filters = uriInfo.getQueryParameters();

		Response retVal = null;
		try {

			retVal = Response.status(200)
					.entity(handle.readMatchingDataRecordsWithPagination(collection, filters, pageNum, pageSize))
					.build();

		} catch (Exception e) {
			logger.error("#####-#### Fetch data with pagination Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@GET
	@Path("/data-records/fetch-record-count")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response fetchMatchingPageCount(@Context UriInfo uriInfo, @QueryParam("table") String collection) {

		Response retVal = null;
		MultivaluedMap<String, String> filters = uriInfo.getQueryParameters();

		try {

			retVal = Response.status(200).entity(handle.readMatchingDataRecordCount(collection, filters)).build();

		} catch (Exception e) {
			logger.error("#####-#### Read data Count Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@PUT
	@Path("/data-records/{objectid}")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response updatedata(@Context HttpServletRequest request, @PathParam("objectid") String id,
			@QueryParam("table") String collection) {

		Response retVal = null;

		StringBuilder buffer = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));) {

			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line);
			}
			logger.info("#####-#### mockservice service - update - input data " + buffer.toString());

			retVal = Response.status(200).entity(handle.updateDataRecords(collection, id, buffer.toString())).build();
		} catch (Exception e) {

			logger.error("#####-#### Update data Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@DELETE
	@Path("/data-records/{objectid}")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response deleteConfigurationWithId(@PathParam("objectid") String id,
			@QueryParam("table") String collection) {

		Response retVal = null;
		try {

			retVal = Response.status(200).entity(handle.deleteDataWithId(collection, id)).build();

		} catch (Exception e) {
			logger.error("#####-#### Delete data by Id Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@POST
	@Path("/data-records/indexes")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response addDataIndexes(@Context HttpHeaders headers, @Context HttpServletRequest request,
			@Context HttpServletResponse response, @QueryParam("isUnique") boolean isUnique,
			@QueryParam("tables") String collection) {

		Response retVal = null;

		StringBuilder buffer = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));) {

			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line);
			}
			logger.info("#####-#### mockservice service - create data indexes- input data " + buffer.toString());

			retVal = Response.status(200).entity(handle.createIndexes(collection, buffer.toString(), isUnique)).build();
		} catch (Exception e) {

			logger.error("#####-#### Add data indexes Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@GET
	@Path("/data-records/indexes")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response getDataIndexes(@QueryParam("tables") String collection) throws Exception {
		Response retVal = null;
		try {

			retVal = Response.status(200).entity(handle.listIndexes(collection)).build();

		} catch (Exception e) {
			logger.error("#####-#### List data Indexes Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@DELETE
	@Path("/data-records/indexes/{indexName}")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response deleteDataIndex(@PathParam("indexName") String indexName, @QueryParam("tables") String collection)
			throws Exception {
		Response retVal = null;
		try {

			handle.dropConfigurationIndexes(indexName);
			retVal = Response.status(200).build();

		} catch (Exception e) {
			logger.error("#####-#### drop data index  Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@DELETE
	@Path("/collections/{collectionName}")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response dropCollection(@PathParam("collectionName") String collectionName) throws Exception {
		Response retVal = null;
		try {

			handle.dropCollection(collectionName);
			retVal = Response.status(200).build();

		} catch (Exception e) {
			logger.error("#####-#### drop collection Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@POST
	@Path("/collections/bulk-delete")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response dropCollections(@Context HttpHeaders headers, @Context HttpServletRequest request,
			@Context HttpServletResponse response) {
		StringBuilder buffer = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));) {
			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line);
			}
			logger.info("#####-#### Bulk delete collections: " + buffer.toString());
			Map<String, Object> result = handle.dropCollections(buffer.toString());
			return Response.status(200).entity(result).build();
		} catch (Exception e) {
			logger.error("#####-#### Bulk delete collections failed", e.fillInStackTrace());
			Map<String, String> error = new HashMap<>();
			error.put("error", e.getMessage());
			return Response.status(500).entity(error).build();
		}
	}

	@GET
	@Path("/collections")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response getCollectionList() throws Exception {
		Response retVal = null;
		try {

			retVal = Response.status(200).entity(handle.getCollectionList()).build();

		} catch (Exception e) {
			logger.error("#####-#### List database collections Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}

		return retVal;
	}

	@POST
	@Path("/configurationsList")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response addConfigurationList(@Context HttpHeaders headers, @Context HttpServletRequest request,
			@Context HttpServletResponse response) {
		Response retVal = null;
		StringBuilder buffer = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));) {
			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line);
			}
			logger.info(
					"#####-#### mockservice service - create - input data for ConfigurationsList " + buffer.toString());
			retVal = Response.status(200).entity(handle.addConfigurationsList(buffer.toString())).build();
		} catch (Exception e) {
			logger.error("#####-#### Add Configuration List Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}
		return retVal;
	}

	@POST
	@Path("/data-records/bulk")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response addDataRecordsList(@Context HttpHeaders headers, @Context HttpServletRequest request,
			@Context HttpServletResponse response, @QueryParam("table") String collection) {
		Response retVal = null;
		StringBuilder buffer = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));) {
			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line);
			}
			logger.info(
					"#####-#### mockservice service - bulk insert - table: " + collection + " data: " + buffer.toString());
			retVal = Response.status(200).entity(handle.addDataRecordsList(collection, buffer.toString())).build();
		} catch (Exception e) {
			logger.error("#####-#### Add Data Records List Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}
		return retVal;
	}

	@POST
	@Path("/addAllSvcConfigurations")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response addAllSvcConfigurations(@Context HttpHeaders headers, @Context HttpServletRequest request,
			@Context HttpServletResponse response) {
		Response retVal = null;
		StringBuilder buffer = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));) {
			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line);
			}
			logger.info(
					"#####-#### mockservice service - create - input data for AllSvcConfigurations " + buffer.toString());
			retVal = Response.status(200).entity(handle.addAllSvcConfigurations(buffer.toString())).build();
		} catch (Exception e) {
			logger.error("#####-#### Add All Service Configurations Service Call Failed", e.fillInStackTrace());
			retVal = Response.status(404).entity("error").build();
		}
		return retVal;
	}

	@POST
	@Path("/gc")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response forceGarbageCollection() {
		try {
			long beforeGC = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
			System.gc();
			Thread.sleep(100); // Brief pause for GC
			long afterGC = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

			Map<String, Object> result = new HashMap<>();
			result.put("memoryBeforeGC_MB", beforeGC / (1024 * 1024));
			result.put("memoryAfterGC_MB", afterGC / (1024 * 1024));
			result.put("memoryFreed_MB", (beforeGC - afterGC) / (1024 * 1024));
			result.put("message", "Garbage collection completed");

			return Response.status(200).entity(result).build();
		} catch (Exception e) {
			logger.error("GC failed", e);
			return Response.status(500).entity("GC failed").build();
		}
	}

	@GET
	@Path("/jvm-performance")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response getJvmPerformance() {
		try {
			Runtime runtime = Runtime.getRuntime();
			long usedMemory = runtime.totalMemory() - runtime.freeMemory();
			long usedMemoryMB = usedMemory / (1024 * 1024);

			// Track min/max
			minMemoryMB.updateAndGet(val -> Math.min(val, usedMemoryMB));
			maxMemoryMB.updateAndGet(val -> Math.max(val, usedMemoryMB));

			Map<String, Object> performance = new HashMap<>();
			performance.put("currentMemoryMB", usedMemoryMB);
			performance.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
			performance.put("memoryUsagePercent", (usedMemory * 100) / runtime.totalMemory());
			performance.put("minObservedMB", minMemoryMB.get() == Long.MAX_VALUE ? usedMemoryMB : minMemoryMB.get());
			performance.put("maxObservedMB", maxMemoryMB.get());

			// K8s recommendations
			long recommendedLimit = Math.max(512, maxMemoryMB.get() + 128);
			performance.put("k8sMemoryLimit", recommendedLimit + "Mi");

			// Warning
			if ((usedMemory * 100) / runtime.totalMemory() > 80) {
				performance.put("warning", "High memory usage - potential OOM");
			}

			return Response.status(200).entity(performance).build();
		} catch (Exception e) {
			logger.error("Performance check failed", e);
			return Response.status(500).entity("Error").build();
		}
	}

	@GET
	@Path("/health")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response healthCheck() {
		Map<String, String> health = new HashMap<>();
		health.put("status", "UP");
		health.put("timestamp", new java.util.Date().toString());
		return Response.status(200).entity(health).build();
	}

	@GET
	@Path("/autoGenId")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response autoGenID(@QueryParam("table") String collection, @QueryParam("field") String field,
			@QueryParam("prefix") String prefix, @QueryParam("numWidth") int numWidth) throws Exception {
		return Response.status(200).entity(handle.generateAutoGenID(collection, field, prefix, numWidth)).build();
	}

	@POST
	@Path("/data-migrator")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response migrateData(@Context HttpHeaders headers, @Context HttpServletRequest request,
			@Context HttpServletResponse response, @QueryParam("sourceUrl") String sourceUrl) {
		StringBuilder buffer = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));) {
			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line);
			}
			logger.info("#####-#### Data migration started for tables: " + buffer.toString());

			Map<String, Object> result = handle.migrateData(buffer.toString(), sourceUrl);
			return Response.status(200).entity(result).build();
		} catch (Exception e) {
			logger.error("#####-#### Data Migration Failed", e.fillInStackTrace());
			Map<String, String> error = new HashMap<>();
			error.put("error", e.getMessage());
			return Response.status(500).entity(error).build();
		}
	}

	@POST
	@Path("/bulkInsert/{tableName}")
	@Consumes({ MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON, "text/csv" })
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response bulkInsert(@Context HttpHeaders headers, @Context HttpServletRequest request,
			@PathParam("tableName") String tableName, @QueryParam("autogenFieldName") String autogenFieldName) {
		Response retVal = null;
		StringBuilder buffer = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));) {
			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line).append("\n");
			}
			retVal = Response.status(200).entity(handle.bulkInsert(tableName, buffer.toString(), autogenFieldName))
					.build();
		} catch (Exception e) {
			logger.error("Bulk insert failed", e);
			retVal = Response.status(500).entity("Bulk insert failed: " + e.getMessage()).build();
		}
		return retVal;
	}

}
