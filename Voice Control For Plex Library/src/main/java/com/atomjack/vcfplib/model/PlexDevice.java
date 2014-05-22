package com.atomjack.vcfplib.model;

import org.simpleframework.xml.Attribute;

public abstract class PlexDevice {
  @Attribute
  public String name;
  @Attribute
	public String address;
  @Attribute
	public String port;
  @Attribute
	public String version;
  @Attribute(required=false)
	public String product;



  public String getName() {
    return name == null ? "" : name;
  }
  public void setName(String name) {
    this.name = name;
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

  public String getVersion() {
    return version;
  }
  public void setVersion(String version) {
    this.version = version;
  }
}
