package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Root(strict=false)
public class PlexVideo extends PlexMedia {
	@Attribute(required=false)
	public String index;


	@Attribute(required=false)
	public String type;
	@Attribute(required=false)
	public String year;
	@ElementList(required=false, inline=true, entry="Genre")
	public ArrayList<Genre> genre;

	@Attribute(required=false)
	public String summary;
  @Attribute(required=false)
  public String originallyAvailableAt;
  public String showTitle;

	public Date airDate() {
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    try {
      return formatter.parse(originallyAvailableAt);
    } catch (Exception ex) {
      ex.printStackTrace();
      return null;
    }
  }

  @Override
  public boolean isMovie() {
    return type.equals("movie");
  }

  @Override
  public boolean isShow() {
    return type.equals("episode");
  }

  @Override
  public boolean isClip() {
    return type.equals("clip");
  }

  @Override
	public String getTitle() {
		return type.equals("movie") ? title : showTitle;
	}

	@Override
	public String getEpisodeTitle() {
		return type.equals("episode") ? title : "";
	}

	@Override
	public String getSummary() {
		return summary;
	}

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
		if(TimeUnit.MILLISECONDS.toHours((long)duration) > 0) {
		return String.format("%d hr %d min",
				TimeUnit.MILLISECONDS.toHours((long)duration),
				TimeUnit.MILLISECONDS.toMinutes((long)duration) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours((long)duration)));
		} else {
			return String.format("%d min", TimeUnit.MILLISECONDS.toMinutes((long)duration));
		}
	}

  @Override
	public void writeToParcel(Parcel out, int flags) {
    super.writeToParcel(out, flags);
		out.writeString(index);
		out.writeString(type);
		out.writeString(year);
		out.writeString(summary);
		out.writeString(originallyAvailableAt);
		out.writeString(showTitle);
		out.writeTypedList(genre);
	}

	public PlexVideo(Parcel in) {
    super(in);
		index = in.readString();
		type = in.readString();
		year = in.readString();
		summary = in.readString();
		originallyAvailableAt = in.readString();
		showTitle = in.readString();
    genre = new ArrayList<Genre>();
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


@Root(strict=false)
class Genre implements Parcelable {
	@Attribute
	public String tag;
	@Attribute(required=false)
	public int id;

	@Override
	public String toString() {
		return tag;
	}

	public Genre() {
		super();
	}

	protected Genre(Parcel in) {
		tag = in.readString();
		id = in.readInt();
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeString(tag);
		out.writeInt(id);
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

