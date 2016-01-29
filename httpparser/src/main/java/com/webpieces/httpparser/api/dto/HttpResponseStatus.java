package com.webpieces.httpparser.api.dto;

public class HttpResponseStatus {

	private int code;
	private String reason;
	
	public void setKnownStatus(KnownStatusCode status) {
		code = status.getCode();
		reason = status.getReason();
	}
	
	public KnownStatusCode getKnownStatus() {
		return KnownStatusCode.lookup(code); 
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + code;
		result = prime * result + ((reason == null) ? 0 : reason.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		HttpResponseStatus other = (HttpResponseStatus) obj;
		if (code != other.code)
			return false;
		if (reason == null) {
			if (other.reason != null)
				return false;
		} else if (!reason.equals(other.reason))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "HttpResponseStatus [code=" + code + ", reason=" + reason + "]";
	}
	
}
