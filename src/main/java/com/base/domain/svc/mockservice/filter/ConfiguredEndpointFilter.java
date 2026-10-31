package com.base.domain.svc.mockservice.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.base.domain.svc.mockservice.core.ConfiguredRequestProcessor;
import com.base.domain.svc.mockservice.utils.ServiceConstants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * (0) This filter is the entry point for mock services utility to function (1)
 * It intercepts all restful service request and processes it (2) This utility
 * can be used to mock any restful services without writing any
 * code/implementation
 * 
 * 
 */

public class ConfiguredEndpointFilter implements Filter {

	// Log Handle
	private static Logger log = LogManager.getLogger(ConfiguredEndpointFilter.class.getName());
	
	// Singleton processor instance to reduce object creation
	private static final ConfiguredRequestProcessor processor = new ConfiguredRequestProcessor();

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	/**
	 * Intercepts any restful service request and processes it without actually
	 * implementing the restful service
	 * 
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		String retVal = null;

		// Cast the request and response to HTTP type for easy usage and access to
		// required methods
		HttpServletRequest httpServletRequest = (HttpServletRequest) request;
		HttpServletResponse httpServletResponse = (HttpServletResponse) response;

		// added for non ASCII support
		httpServletResponse.setCharacterEncoding(StandardCharsets.UTF_8.toString());

		// Start using the wrapper http servlet request object for safe reads
		CachedBodyHttpServletRequest wrapperRequest = new CachedBodyHttpServletRequest(httpServletRequest);

		// Get the restful service URI.
		// Later, We use this a key to retrieve corresponding restful configuration from
		// DB
		String uri = URLDecoder.decode(wrapperRequest.getRequestURI().toString(), StandardCharsets.UTF_8.toString());

		log.info("### getRequestURI : " + uri);

		try {
			retVal = processor.process(uri, httpServletRequest.getParameterMap(), wrapperRequest.getBody(),
					httpServletRequest.getMethod());
		} catch (Exception e) {
			log.error("#####-#### Error processing the request", e.fillInStackTrace());
			retVal = ServiceConstants.ERROR_MSG_CHECK_LOGS;
		}

		// If corresponding configuration is found, the return value will NOT be null
		// This could be both a success scenario or error (Not Exception) scenario
		// either way the value returned by the processor will not be bull
		// so process it and send it back to the client
		//
		// And of course no further processing is required, so avoid calling do chain
		// method
		if (retVal != null) {

			// Post processing the restful service request
			// write the return data into response and close it

			PrintWriter writer = httpServletResponse.getWriter();
			writer.write(retVal);
			writer.close();
			return;
		}
		//
		// In case corresponding configuration is not found
		// then allow the control to flow further
		// there might be some restful service that can handle the request
		//
		else {
			chain.doFilter(wrapperRequest, response);
		}

	}

	@Override
	public void destroy() {
	}

}
