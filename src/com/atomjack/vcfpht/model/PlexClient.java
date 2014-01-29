package com.atomjack.vcfpht.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

import com.atomjack.vcfpht.Logger;

@Root(name="Server", strict=false)
public class PlexClient extends PlexDevice {
	@Attribute
	private String host;

	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}

	public float getNumericVersion() {
		if(this.version == null)
			return 0;
    try {
      String v = version.substring(0, version.indexOf(".")) + "." + version.substring(version.indexOf(".")+1).replaceAll("\\.", "");
      v = v.split("-")[0];
      v = v.replaceAll("[^0-9.]", "");
      Logger.d("Numeric Version: %s", v);

      return Float.parseFloat(v);
    } catch (Exception ex) {
      ex.printStackTrace();
      Logger.d("Exception getting version: %f", version);
      return 0;
    }
	}


}
