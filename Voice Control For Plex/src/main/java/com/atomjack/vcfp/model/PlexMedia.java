package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexMedia implements Parcelable {
	@Attribute
	public String key;
	@Attribute
	public String title;
	@Attribute(required=false)
	public String viewOffset;
	@Attribute(required=false)
	public PlexServer server;
	@Attribute(required=false)
	public String grandparentTitle;
	@Attribute(required=false)
	public String grandparentThumb;
	@Attribute(required=false)
	public int duration;
	@Attribute(required=false)
	public String thumb;

	public String getTitle() {
		return title;
	}

	// PlexVideo will override this and return the episode title if it's a show, or "" if not
	public String getEpisodeTitle() {
		return "";
	}

	public String getSummary() {
		return "";
	}

	public String getArtUri() {
		return "";
	}

	public String getThumbUri() {
		return String.format("%s%s", server.activeConnection.uri, thumb);
	}

	@Override
	public void writeToParcel(Parcel parcel, int i) {

	}

	@Override
	public int describeContents() {
		return 0;
	}
}
