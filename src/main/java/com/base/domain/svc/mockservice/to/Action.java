package com.base.domain.svc.mockservice.to;

import java.util.List;

public class Action {

	private String type;
	private String table;
	private boolean isMany = false;
	private List<Criteria> criteria;
	private Object fixedData;
	private String version = "1.0"; // Will be useful in case we change the configuration structure

	public Action() {
	}

	public Action(String type, String table, boolean isMany, List<Criteria> criteria, Object fixedData,
			String version) {
		super();
		this.type = type;
		this.table = table;
		this.isMany = isMany;
		this.criteria = criteria;
		this.fixedData = fixedData;
		this.version = version;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getTable() {
		return table;
	}

	public void setTable(String table) {
		this.table = table;
	}

	public List<Criteria> getCriteria() {
		return criteria;
	}

	public void setCriteria(List<Criteria> criteria) {
		this.criteria = criteria;
	}

	public boolean isMany() {
		return isMany;
	}

	public void setMany(boolean isMany) {
		this.isMany = isMany;
	}

	public Object getFixedData() {
		return fixedData;
	}

	public void setFixedData(Object fixedData) {
		this.fixedData = fixedData;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	@Override
	public String toString() {
		return "Action [type=" + type + ", table=" + table + ", isMany=" + isMany + ", criteria=" + criteria
				+ ", fixedData=" + fixedData + ", version=" + version + "]";
	}

}
