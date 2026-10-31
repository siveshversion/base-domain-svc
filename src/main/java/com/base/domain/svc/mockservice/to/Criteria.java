package com.base.domain.svc.mockservice.to;

import java.util.List;

public class Criteria {

	private String source;
	private String refString;
	private String targetAttribute;
	private List<String> fieldsToReturn;
	private String type;
	private String dataType = "object";
	private String version = "1.0";

	public Criteria() {
	}

	public Criteria(String source, String refString, String targetAttribute, List<String> fieldsToReturn, String type,
			String dataType, String version) {
		super();
		this.source = source;
		this.refString = refString;
		this.targetAttribute = targetAttribute;
		this.fieldsToReturn = fieldsToReturn;
		this.type = type;
		this.dataType = dataType;
		this.version = version;
	}

	public String getSource() { return source; }
	public void setSource(String source) { this.source = source; }

	public String getRefString() { return refString; }
	public void setRefString(String refString) { this.refString = refString; }

	public String getTargetAttribute() { return targetAttribute; }
	public void setTargetAttribute(String targetAttribute) { this.targetAttribute = targetAttribute; }

	public List<String> getFieldsToReturn() { return fieldsToReturn; }
	public void setFieldsToReturn(List<String> fieldsToReturn) { this.fieldsToReturn = fieldsToReturn; }

	public String getType() { return type; }
	public void setType(String type) { this.type = type; }

	public String getDataType() { return dataType; }
	public void setDataType(String dataType) { this.dataType = dataType; }

	public String getVersion() { return version; }
	public void setVersion(String version) { this.version = version; }

	@Override
	public String toString() {
		return "Criteria [source=" + source + ", refString=" + refString + ", targetAttribute=" + targetAttribute
				+ ", fieldsToReturn=" + fieldsToReturn + ", type=" + type + ", dataType=" + dataType + ", version="
				+ version + "]";
	}
}
