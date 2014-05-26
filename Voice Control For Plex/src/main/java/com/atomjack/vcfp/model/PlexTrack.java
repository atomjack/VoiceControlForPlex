package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexTrack implements Parcelable {
	@Attribute
	public String key;
	@Attribute
	public String title;
	@Attribute(required=false)
	public String thumb;
	@Attribute(required=false)
	public String parentThumb;
	@Attribute(required=false)
	public String parentTitle;
	@Attribute(required=false)
	public String grandparentTitle;
	@Attribute(required=false)
	public String viewOffset;

	public String artist;
	public String album;

	public PlexServer server;
	
	public PlexServer getServer() {
		return server;
	}

	public void setServer(PlexServer server) {
		this.server = server;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	public PlexTrack() {

	}

	@Override
	public void writeToParcel(Parcel out, int i) {
		out.writeString(key);
		out.writeString(title);
		out.writeString(thumb);
		out.writeString(parentThumb);
		out.writeString(parentTitle);
		out.writeString(grandparentTitle);
		out.writeString(viewOffset);
		out.writeString(artist);
		out.writeString(album);
		out.writeParcelable(server, i);
	}

	public PlexTrack(Parcel in) {
		key = in.readString();
		title = in.readString();
		thumb = in.readString();
		parentThumb = in.readString();
		parentTitle = in.readString();
		grandparentTitle = in.readString();
		viewOffset = in.readString();
		artist = in.readString();
		album = in.readString();
		server = in.readParcelable(PlexServer.class.getClassLoader());
	}

	public static final Parcelable.Creator<PlexTrack> CREATOR = new Parcelable.Creator<PlexTrack>() {
		public PlexTrack createFromParcel(Parcel in) {
			return new PlexTrack(in);
		}

		public PlexTrack[] newArray(int size) {
			return new PlexTrack[size];
		}
	};
}
