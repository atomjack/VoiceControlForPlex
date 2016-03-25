package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.atomjack.vcfp.Utils;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Commit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

@Root(strict=false)
public class Connection implements Parcelable {
	@Attribute
	public String protocol;
	@Attribute
	public String address;
	@Attribute
	public String port;

	public Connection() {}

	public Connection(String _protocol, String _address, String _port) {
		protocol = _protocol;
		address = _address;
		port = _port;
		uri = String.format("%s://%s:%s", protocol, address, port);
		try {
			InetAddress ad = InetAddress.getByName(address);
			local = ad.isSiteLocalAddress();
		} catch (UnknownHostException e) {
			local = false;
			e.printStackTrace();
		}
	}

	@Override
	public int describeContents() {
		return 0;
	}


	public String toString() {
    String out = String.format("%s://%s:%s", protocol, address, port);
//		String out = String.format("Address: %s", address);
//		out += String.format("Port: %s", port);
//		out += String.format("Protocol: %s", protocol);
		return out;
	}

	@Attribute
	public String uri;
	public boolean local;

	@Attribute(required=false, name="local")
	private int localStr;

	@Commit
	public void build() {
		local = localStr == 1;
	}

	@Override
	public void writeToParcel(Parcel parcel, int i) {
		parcel.writeString(protocol);
		parcel.writeString(address);
		parcel.writeString(port);
		parcel.writeString(uri);
		parcel.writeInt(local ? 1 : 0);
	}

	public Connection(Parcel in) {
		protocol = in.readString();
		address = in.readString();
		port = in.readString();
		uri = in.readString();
		local = in.readInt() == 1;
	}

	public static final Parcelable.Creator<Connection> CREATOR = new Parcelable.Creator<Connection>() {
		public Connection createFromParcel(Parcel in) {
			return new Connection(in);
		}
		public Connection[] newArray(int size) {
			return new Connection[size];
		}
	};

  // Is the device's current IP address on the same subnet as this connection?
  public boolean isOnSameNetwork() {
    try {
      byte[] a1 = InetAddress.getByName(address).getAddress();
      byte[] a2 = InetAddress.getByName(Utils.getIPAddress(true)).getAddress();
      byte[] m = InetAddress.getByName("255.255.255.0").getAddress();

      for (int i = 0; i < a1.length; i++)
        if ((a1[i] & m[i]) != (a2[i] & m[i]))
          return false;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return true;
  }

  /* Is this connection's address on a private network?
  *  10.0.0.0        -   10.255.255.255
  *  172.16.0.0      -   172.31.255.255
  *  192.168.0.0     -   192.168.255.255
  */
  public boolean isPrivateV4Address() {
    Pattern patternPrivate = Pattern.compile("^(10(\\.([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])){3})$|" +
            "^((172\\.1[6-9])|(172\\.2[0-9])|(172\\.3[0-1]))(\\.([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])){2}$|" +
            "^192\\.168(\\.([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])){2}$");
    return patternPrivate.matcher(address).matches();
  }
}
