package com.base.domain.svc.mockservice.to;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigMap {
	private String url;
	private String verb;
	private String description;
	private String group;
	private List<Action> actions;
	private String returnType = "object"; // convert to enum
	private Map<String, String> headers = new HashMap<>(); // Any headers that need to be send back
	private String version = "1.0"; // Will be useful in case we change the configuration structure

	public ConfigMap() {
	}

	public ConfigMap(String url, String verb, String description, String group, List<Action> actions, String returnType,
			Map<String, String> headers, String version) {
		super();
		this.url = url;
		this.verb = verb;
		this.description = description;
		this.group = group;
		this.actions = actions;
		this.returnType = returnType;
		this.headers = headers;
		this.version = version;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getReturnType() {
		return returnType;
	}

	public void setReturnType(String returnType) {
		this.returnType = returnType;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	public String getVerb() {
		return verb;
	}

	public void setVerb(String verb) {
		this.verb = verb;
	}

	public List<Action> getActions() {
		return actions;
	}

	public void setActions(List<Action> actions) {
		this.actions = actions;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	@Override
	public String toString() {
		return "ConfigMap [url=" + url + ", verb=" + verb + ", description=" + description + ", group=" + group
				+ ", actions=" + actions + ", returnType=" + returnType + ", headers=" + headers + ", version="
				+ version + "]";
	}

}
