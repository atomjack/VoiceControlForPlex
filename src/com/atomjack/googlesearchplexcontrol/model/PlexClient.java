package com.atomjack.googlesearchplexcontrol.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

import android.util.Log;

@Root(name="Server", strict=false)
public class PlexClient {
	@Attribute
	private String name;
	@Attribute
	private String host;
	@Attribute
	private String address;
	@Attribute
	private String port;
	@Attribute
	private String version;
	
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public String getAddress() {
		return address;
	}
	public void setAddress(String address) {
		this.address = address;
	}
	public String getPort() {
		return port;
	}
	public void setPort(String port) {
		this.port = port;
	}
	public float getNumericVersion() {
		if(this.version == null)
			return 0;
		String[] v = this.version.split("\\.");
		
		String foo = this.version.substring(0, this.version.indexOf(".")) + "." + this.version.substring(this.version.indexOf(".")+1).replaceAll("\\.", "");
		foo = foo.split("-")[0];
		Log.v("GoogleSearchPlexControl", "foo: " + foo);
		
		float a = Float.parseFloat(foo);
		return a;
	}
	
}
