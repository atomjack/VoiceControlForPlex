package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.atomjack.vcfp.Logger;

import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexServer extends PlexDevice {

  public PlexServer() {}
  public PlexServer(String name) {
    this.name = name;
  }

	public static PlexServer fromDevice(Device device) {
		Logger.d("Creating server %s", device.name);
		PlexServer server = new PlexServer(device.name);
		server.sourceTitle = device.sourceTitle;
		server.connections = device.connections;
		server.address = "";
		if(!device.owned) {
			Logger.d("Not owned, has %d connections.", device.connections.size());
			for(Connection connection : device.connections) {
				Logger.d("Connection %s: %s", connection.address, connection.local);
				if(!connection.local) {
					server.address = connection.address;
					server.port = connection.port;
					break;
				}
			}
		} else {
			if(server.connections != null && server.connections.size() > 0) {
				server.address = server.connections.get(0).address;
				server.port = server.connections.get(0).port;
			}
		}
		server.machineIdentifier = device.clientIdentifier;
		server.owned = device.owned;
		server.product = device.product;
		server.accessToken = device.accessToken;
		return server;
	}

	public String sourceTitle;

	public List<String> movieSections = new ArrayList<String>();
	public List<String> tvSections = new ArrayList<String>();
	public List<String> musicSections = new ArrayList<String>();

  public int movieSectionsSearched = 0;
  public int showSectionsSearched = 0;
  public int musicSectionsSearched = 0;

	public boolean owned = true;
	public String accessToken;

	public void addMovieSection(String key) {
		if(!movieSections.contains(key)) {
			movieSections.add(key);
		}
	}

	public void addTvSection(String key) {
		if(!tvSections.contains(key)) {
			tvSections.add(key);
		}
	}
	
	public void addMusicSection(String key) {
		if(!musicSections.contains(key)) {
			musicSections.add(key);
		}
	}
	
	@Override
	public String toString() {
		String output = "";
		output += "Name: " + name + "\n";
		output += "IP Address: " + address + "\n";
		return output;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel parcel, int i) {
		parcel.writeString(name);
		parcel.writeString(port);
		parcel.writeString(version);
		parcel.writeString(product);
		parcel.writeString(address);
		parcel.writeString(accessToken);
	}

	public PlexServer(Parcel in) {
		name = in.readString();
		port = in.readString();
		version = in.readString();
		product = in.readString();
		address = in.readString();
		accessToken = in.readString();
	}

	public static final Parcelable.Creator<PlexServer> CREATOR = new Parcelable.Creator<PlexServer>() {
		public PlexServer createFromParcel(Parcel in) {
			return new PlexServer(in);
		}

		public PlexServer[] newArray(int size) {
			return new PlexServer[size];
		}
	};
}
