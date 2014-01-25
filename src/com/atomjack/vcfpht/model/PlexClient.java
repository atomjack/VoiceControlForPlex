package com.atomjack.vcfpht.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

import android.util.Log;

import com.atomjack.vcfpht.MainActivity;

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
		String[] v = super.version.split("\\.");
		
		String foo = super.version.substring(0, super.version.indexOf(".")) + "." + super.version.substring(super.version.indexOf(".")+1).replaceAll("\\.", "");
		foo = foo.split("-")[0];
    foo = foo.replaceAll("[^0-9.]", "");
		Log.v(MainActivity.TAG, "Numeric Version: " + foo);
		
		float a = Float.parseFloat(foo);
		return a;
	}


}
