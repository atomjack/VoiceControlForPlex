package com.atomjack.vcfp.model;

import org.simpleframework.xml.Attribute;

public abstract class PlexDevice {
  @Attribute
  protected String name;
  @Attribute
  protected String address;
  @Attribute
  protected String port;
  @Attribute
  protected String version;
  @Attribute(required=false)
  protected String product;



  public String getName() {
    return name == null ? "" : name;
  }
  public void setName(String name) {
    this.name = name;
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

  public String getProduct()
  {
    return product;
  }
  public void setProduct(String product)
  {
    this.product = product;
  }

  public String getVersion() {
    return version;
  }
  public void setVersion(String version) {
    this.version = version;
  }
}
