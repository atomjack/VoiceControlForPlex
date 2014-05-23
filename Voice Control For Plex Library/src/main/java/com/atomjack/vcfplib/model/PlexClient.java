package com.atomjack.vcfplib.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(name="Server", strict=false)
public class PlexClient extends PlexDevice {
	@Attribute
	public String host;

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
		parcel.writeString(host);
	}

	public PlexClient(Parcel in) {
		name = in.readString();
		port = in.readString();
		version = in.readString();
		product = in.readString();
		host = in.readString();
	}

	public static final Parcelable.Creator<PlexClient> CREATOR = new Parcelable.Creator<PlexClient>() {
		public PlexClient createFromParcel(Parcel in) {
			return new PlexClient(in);
		}

		public PlexClient[] newArray(int size) {
			return new PlexClient[size];
		}
	};
}
