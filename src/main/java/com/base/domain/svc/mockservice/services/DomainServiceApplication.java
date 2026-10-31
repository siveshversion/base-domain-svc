package com.base.domain.svc.mockservice.services;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/")
public class DomainServiceApplication extends Application {

	@Override
	public Set<Class<?>> getClasses() {
		Set<Class<?>> serviceClasses = new HashSet<>();
		serviceClasses.add(HealthCheckResource.class);
		serviceClasses.add(ManagementResource.class);
		return serviceClasses;
	}
}
