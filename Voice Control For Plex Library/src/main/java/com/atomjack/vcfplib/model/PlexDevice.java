package com.atomjack.vcfplib.model;

import android.os.Parcelable;

import org.simpleframework.xml.Attribute;

public abstract class PlexDevice implements Parcelable {
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

  public String getVersion() {
    return version;
  }
  public void setVersion(String version) {
    this.version = version;
  }
}
