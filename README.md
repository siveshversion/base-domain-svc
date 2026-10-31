# Base Domain Service

A Java web application that uses JSON configuration to mock REST endpoints. It matches incoming requests to configurations stored in MongoDB, performs the configured data operation, and returns the result.

> **Development and testing only. Do not expose this service in production.** Its management endpoints can modify or delete MongoDB data and do not provide authentication.

## Requirements

- Java 21 or later
- Maven
- MongoDB

## Build and test

```shell
mvn clean test
mvn package
```

The WAR is created in `target/`. Deploy it to a Jakarta-compatible servlet container with RESTEasy support.

## MongoDB configuration

The service uses the following environment variables. If unset, the default is shown.

| Variable | Default | Description |
|---|---|---|
| `DOMAIN_SVC_MONGODB_HOST_WITH_CREDENTIALS` | `localhost:27017` | MongoDB host and optional connection details. The service prefixes this value with `mongodb://`. |
| `DOMAIN_SVC_MONGODB_DATABASE` | `domain-service-db` | Database name. |
| `DOMAIN_SVC_MONGODB_CONFIG_COLLECTION` | `endpoint-configurations` | Collection containing endpoint configurations. |
| `DOMAIN_SVC_KAFKA_BOOTSTRAP_SERVERS` | Not set | Kafka bootstrap server list, for example `localhost:9092`. |
| `DOMAIN_SVC_KAFKA_TOPIC_PREFIX` | Not set | Kafka topic prefix. Each MongoDB collection publishes to `<prefix>.<collection>`. |

Example for PowerShell:

```powershell
$env:DOMAIN_SVC_MONGODB_HOST_WITH_CREDENTIALS = "localhost:27017"
$env:DOMAIN_SVC_MONGODB_DATABASE = "domain-service-db"
$env:DOMAIN_SVC_MONGODB_CONFIG_COLLECTION = "endpoint-configurations"
$env:DOMAIN_SVC_KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
$env:DOMAIN_SVC_KAFKA_TOPIC_PREFIX = "domain-events"
```

After a successful MongoDB create, update, delete, array update, or collection drop, the service asynchronously publishes a JSON event to Kafka. Events contain `collection`, `operation`, `recordId` (null when unavailable, such as a multi-record operation), and an ISO-8601 `timestamp`. Kafka publishing is disabled when either Kafka environment variable is unset. Publish errors are logged; the MongoDB operation response remains successful if the database write already succeeded.

## Endpoint configuration

Each configuration describes a URL path, HTTP verb, and one or more actions. Configurations are stored in the MongoDB mapper collection.

```json
{
  "url": "/demo-records/{{recordId}}",
  "verb": "get",
  "description": "Read one record by recordId",
  "group": "demo-records",
  "actions": [
    {
      "type": "read",
      "table": "demo-records",
      "criteria": [
        {
          "source": "path-param",
          "refString": "recordId",
          "targetAttribute": "recordId",
          "type": "filter"
        }
      ]
    }
  ]
}
```

This configuration reads a record from the `demo-records` collection, using the `recordId` path parameter to match the record's `recordId` field. It matches the GET-by-ID example in the demo configuration file below.

Supported action types include `create`, `read`, `update`, `delete`, `update-array-element`, `update-array-add-element`, `update-array-delete-element`, and `fixed-return`. The [`demo-records-configurations.json`](src/main/resources/demo-records-configurations.json) file contains concise examples for these actions using the `demo-records` collection. It also demonstrates GET, POST, PUT, PATCH, and DELETE requests. PATCH uses the `update` action type with criteria for the fields to change.

## Management API

Management endpoints are rooted at `/mock`:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/mock/configurations` | Add a configuration. |
| `GET` | `/mock/configurations` | List or filter configurations. |
| `GET` | `/mock/configurations/paging` | List configurations with pagination (`pagenum`, `pageSize`). |
| `PUT` | `/mock/configurations/{objectid}` | Replace a configuration. |
| `DELETE` | `/mock/configurations/{objectid}` | Delete a configuration. |
| `POST` | `/mock/data-records?table={collection}` | Add data to a collection. |
| `GET` | `/mock/data-records/paging?table={collection}` | List collection data with pagination. |
| `PUT` | `/mock/data-records/{objectid}?table={collection}` | Update a data record. |
| `DELETE` | `/mock/data-records/{objectid}?table={collection}` | Delete a data record. |
| `GET` | `/mock/collections` | List database collections. |
| `DELETE` | `/mock/collections/{collectionName}` | Drop a collection. |
| `GET` | `/mock/env-info` | List service environment keys and effective values. MongoDB credentials are redacted. |
| `GET` | `/health` | Health check. |

The API also includes collection count, index, bulk-data, and maintenance endpoints. See [`ManagementResource.java`](src/main/java/com/base/domain/svc/mockservice/services/ManagementResource.java) for the complete route list.
