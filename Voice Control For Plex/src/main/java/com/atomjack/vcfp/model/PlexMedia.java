package com.atomjack.vcfp.model;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.handlers.BitmapHandler;
import com.atomjack.vcfp.handlers.InputStreamHandler;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.loopj.android.http.BinaryHttpResponseHandler;

import org.apache.http.Header;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Root(strict=false)
public abstract class PlexMedia implements Parcelable {
  public enum IMAGE_KEY {
    NOTIFICATION_THUMB
  }

  public static final int TYPE_MOVIE = 0;
  public static final int TYPE_SHOW = 1;
  public static final int TYPE_MUSIC = 2;

  public boolean isMovie() {
    return false;
  }

  public boolean isMusic() {
    return this instanceof PlexTrack;
  }

  public boolean isShow() {
    PlexVideo video = (PlexVideo)this;
    return !video.type.equals("movie");
  }

  public boolean isClip() { return false; }

  @Attribute
	public String key;
  @Attribute
  public String ratingKey;
	@Attribute
	public String title;
  @Attribute(required=false)
  public String art;
	@Attribute(required=false)
	public String viewOffset = "0";
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
  @ElementList(required=false, inline=true, entry="Media")
  public List<Media> media = new ArrayList<Media>();

  public int activeSubtitleStream = 0;
  public String parentArt;

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

  public String getDurationTimecode() {
    return VoiceControlForPlexApplication.secondsToTimecode(duration/1000);
  }

  public String getArtUri() {
    String uri = String.format("%s%s", server.activeConnection.uri, art);
    return uri;
  }

	public String getThumbUri() {
		return String.format("%s%s", server.activeConnection.uri, thumb);
	}

	public String getThumbUri(int width, int height) {
		String url = String.format("%s/photo/:/transcode?width=%d&height=%d&url=%s", server.activeConnection.uri,
			width, height, Uri.encode(String.format("127.0.0.1:32400%s", thumb)));
		if(server.accessToken != null)
			url += String.format("&%s=%s", PlexHeaders.XPlexToken, server.accessToken);
		return url;
	}

  public InputStream getThumb(int width, int height) {
    String path = String.format("/photo/:/transcode?width=%d&height=%d&url=%s", width, height, Uri.encode(String.format("http://127.0.0.1:32400%s", thumb)));
    String url = server.buildURL(path);
    byte[] imageData = PlexHttpClient.getSyncBytes(url);
    InputStream is = new ByteArrayInputStream(imageData);
    try {
      is.reset();
    } catch (Exception e) {}
    return is;
  }

	@Override
	public int describeContents() {
		return 0;
	}

  public List<Stream> getStreams() {
    Media m = media.get(0);
    Part p = m.parts.get(0);
    return p.streams;
  }

  public List<Stream> getStreams(int streamType) {
    List<Stream> s = new ArrayList<Stream>();
    try {
      Media m = media.get(0);
      Part p = m.parts.get(0);

      for (Stream stream : p.streams) {
        if (stream.streamType == streamType)
          s.add(stream);
      }
    } catch (Exception ex) {}
    return s;
  }

  public String getPartUri() {
    Media m = media.get(0);
    return String.format("%s%s", server.activeConnection.uri, m.parts.get(0).key);
  }



  public PlexMedia() {

  }

  public void writeToParcel(Parcel out, int flags) {
    out.writeString(key);
    out.writeString(title);
    out.writeString(viewOffset);
    out.writeString(grandparentTitle);
    out.writeString(grandparentThumb);
    out.writeString(thumb);
    out.writeString(art);
    out.writeInt(duration);
    out.writeString(ratingKey);
    out.writeParcelable(server, flags);
    out.writeTypedList(media);
  }

  public PlexMedia(Parcel in) {
    key = in.readString();
    title = in.readString();
    viewOffset = in.readString();
    grandparentTitle = in.readString();
    grandparentThumb = in.readString();
    thumb = in.readString();
    art = in.readString();
    duration = in.readInt();
    ratingKey = in.readString();
    server = in.readParcelable(PlexServer.class.getClassLoader());
    media = new ArrayList<Media>();
    in.readTypedList(media, Media.CREATOR);
  }

  public String getCacheKey(String which) {
    return String.format("%s%s", server.machineIdentifier, which);
  }

  public String getImageKey(IMAGE_KEY imageKey) throws Exception {
    if(server == null)
      throw new Exception("The server for this piece of media must be defined.");
    else {
      return String.format("%s/%s/%s", server.machineIdentifier, ratingKey, imageKey);
    }
  }

}

@Root(strict=false)
class Media implements Parcelable {
  @ElementList(required=false, inline=true, entry="Part")
  public List<Part> parts = new ArrayList<Part>();

  public Media() {

  }

  public Media(Parcel in) {
    parts = new ArrayList<Part>();
    in.readTypedList(parts, Part.CREATOR);
  }
  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeTypedList(parts);
  }

  public static final Parcelable.Creator<Media> CREATOR = new Parcelable.Creator<Media>() {
    public Media createFromParcel(Parcel in) {
      return new Media(in);
    }

    public Media[] newArray(int size) {
      return new Media[size];
    }
  };
}

@Root(strict=false)
class Part implements Parcelable {
  @Attribute(required=false)
  public String id;
  @ElementList(required=false, inline=true, entry="Stream")
  public List<Stream> streams = new ArrayList<Stream>();
  @Attribute(required=false)
  public String key;

  public Part() {

  }

  public Part(Parcel in) {
    id = in.readString();
    streams = new ArrayList<Stream>();
    in.readTypedList(streams, Stream.CREATOR);
    key = in.readString();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(id);
    out.writeTypedList(streams);
    out.writeString(key);
  }

  public static final Parcelable.Creator<Part> CREATOR = new Parcelable.Creator<Part>() {
    public Part createFromParcel(Parcel in) {
      return new Part(in);
    }

    public Part[] newArray(int size) {
      return new Part[size];
    }
  };
}

@Root(strict=false)
class Stream implements Parcelable {
  @Attribute(required=false)
  public String id;
  @Attribute(required=false)
  public int streamType;
  @Attribute(required=false)
  public String language;

  public Stream() {

  }

  public Stream(Parcel in) {
    id = in.readString();
    streamType = in.readInt();
    language = in.readString();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(id);
    out.writeInt(streamType);
    out.writeString(language);
  }

  public static final Parcelable.Creator<Stream> CREATOR = new Parcelable.Creator<Stream>() {
    public Stream createFromParcel(Parcel in) {
      return new Stream(in);
    }

    public Stream[] newArray(int size) {
      return new Stream[size];
    }
  };
}
