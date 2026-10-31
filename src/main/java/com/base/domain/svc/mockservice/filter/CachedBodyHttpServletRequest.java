package com.base.domain.svc.mockservice.filter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 
 * This is a wrapper class to safely read request body content multiple times
 * 
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

	// Log Handle
	private static Logger logger = LogManager.getLogger(CachedBodyHttpServletRequest.class.getName());

	private String body = null;

	public CachedBodyHttpServletRequest(HttpServletRequest request) throws UnsupportedEncodingException {
		super(request);

		// If there is any data shared in the request body, collected it
		// it will used later for processing the restful service request
		StringBuilder buffer = new StringBuilder();

		try (BufferedReader in = new BufferedReader(
				new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8.toString()))) {

			String line = null;
			while ((line = in.readLine()) != null) {
				buffer.append(line);
			}

		} catch (Exception e) {
			logger.error("#####-#### CachedBodyHttpServletRequest - Error Reading Request Body Content", e.fillInStackTrace());
		}

		body = buffer.toString();
	}

	@Override
	public ServletInputStream getInputStream() throws IOException {
		final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body.getBytes());
		ServletInputStream servletInputStream = new ServletInputStream() {
			public int read() throws IOException {
				return byteArrayInputStream.read();
			}

			@Override
			public boolean isFinished() {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public boolean isReady() {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void setReadListener(ReadListener readListener) {
				// TODO Auto-generated method stub
				
			}
		};
		return servletInputStream;
	}

	@Override
	public BufferedReader getReader() throws IOException {
		return new BufferedReader(new InputStreamReader(this.getInputStream()));
	}

	// Use this method to read request content
	public String getBody() {
		return this.body;
	}
}
