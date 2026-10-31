package com.base.domain.svc.mockservice.services;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/health")
public class HealthCheckResource {

	@PermitAll
	@GET
	@Produces("application/json")
	public boolean healthCheck() {
		// Returns true always
		return true;
	}

}
