package com.atomjack.vcfplib.model;

import android.os.Parcel;
import android.os.Parcelable;

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

	@Attribute
	public String machineIdentifier;

	public List<String> movieSections = new ArrayList<String>();
	public List<String> tvSections = new ArrayList<String>();
	public List<String> musicSections = new ArrayList<String>();

  public int movieSectionsSearched = 0;
  public int showSectionsSearched = 0;
  public int musicSectionsSearched = 0;

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
	
	public String getClientsURL() {
		return "http://" + address + ":" + port + "/clients";
	}
	
	public String getBaseURL() {
		return "http://" + address + ":" + port + "/";
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
	}

	public PlexServer(Parcel in) {
		name = in.readString();
		port = in.readString();
		version = in.readString();
		product = in.readString();
		address = in.readString();
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
