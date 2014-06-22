package com.atomjack.vcfp.model;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

@Root(strict=false)
public class PlexVideo implements Parcelable {
	@Attribute
	public String key;
	@Attribute
	public String title;
	@Attribute(required=false)
	public String viewOffset;
	@Attribute(required=false)
	public String index;
	@Attribute(required=false)
	public String grandparentTitle;
	@Attribute(required=false)
	public PlexServer server;
	@Attribute(required=false)
	public String grandparentThumb;
	@Attribute(required=false)
	public String thumb;
	@Attribute(required=false)
	public String art;
	@Attribute(required=false)
	public String type;
	@Attribute(required=false)
	public String year;
	@ElementList(required=false, inline=true, entry="Genre")
	public ArrayList<Genre> genre;
	@Attribute(required=false)
	public String duration;
	@Attribute(required=false)
	public String summary;
  @Attribute(required=false)
  public String originallyAvailableAt;
	public Date airDate() {
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    try {
      return formatter.parse(originallyAvailableAt);
    } catch (Exception ex) {
      ex.printStackTrace();
      return null;
    }
  }
	public String showTitle;

	public PlexVideo() {
		super();
		genre = new ArrayList<Genre>();
	}

	public PlexServer getServer() {
		return server;
	}

	public void setServer(PlexServer server) {
		this.server = server;
	}

	public String getGenres() {
		return TextUtils.join(", ", genre);
	}

	public String getDuration() {
		if(duration == null)
			return "";
		if(TimeUnit.MILLISECONDS.toHours(Long.parseLong(duration)) > 0) {
		return String.format("%d hr %d min", 
				TimeUnit.MILLISECONDS.toHours(Long.parseLong(duration)),
				TimeUnit.MILLISECONDS.toMinutes(Long.parseLong(duration)) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(Long.parseLong(duration))));
		} else {
			return String.format("%d min", TimeUnit.MILLISECONDS.toMinutes(Long.parseLong(duration)));
		}
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeString(key);
		out.writeString(title);
		out.writeString(viewOffset);
		out.writeString(index);
		out.writeString(grandparentTitle);
		out.writeString(grandparentThumb);
		out.writeString(thumb);
		out.writeString(art);
		out.writeString(type);
		out.writeString(year);
		out.writeString(duration);
		out.writeString(summary);
		out.writeString(originallyAvailableAt);
		out.writeString(showTitle);
		out.writeParcelable(server, flags);
		out.writeTypedList(genre);
	}

	public PlexVideo(Parcel in) {
		this();
		key = in.readString();
		title = in.readString();
		viewOffset = in.readString();
		index = in.readString();
		grandparentTitle = in.readString();
		grandparentThumb = in.readString();
		thumb = in.readString();
		art = in.readString();
		type = in.readString();
		year = in.readString();
		duration = in.readString();
		summary = in.readString();
		originallyAvailableAt = in.readString();
		showTitle = in.readString();
		server = in.readParcelable(PlexServer.class.getClassLoader());
		in.readTypedList(genre, Genre.CREATOR);
	}

	public int describeContents() {
		return 0; // TODO: Customise this generated block
	}

	public static final Parcelable.Creator<PlexVideo> CREATOR = new Parcelable.Creator<PlexVideo>() {
		public PlexVideo createFromParcel(Parcel in) {
			return new PlexVideo(in);
		}

		public PlexVideo[] newArray(int size) {
			return new PlexVideo[size];
		}
	};
}



class Genre implements Parcelable {
	@Attribute
	public String tag;
	
	@Override
	public String toString() {
		return tag;
	}

	public Genre() {
		super();
	}

	protected Genre(Parcel in) {
		tag = in.readString();
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeString(tag);
	}

	public int describeContents() {
		return 0; // TODO: Customise this generated block
	}

	public static final Parcelable.Creator<Genre> CREATOR = new Parcelable.Creator<Genre>() {
		public Genre createFromParcel(Parcel in) {
			return new Genre(in);
		}

		public Genre[] newArray(int size) {
			return new Genre[size];
		}
	};
}

