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
	@Attribute
	public String key;
	@Attribute
	public String title;
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
		return "";
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

	public void getThumb(int width, int height, final BitmapHandler bitmapHandler) {
		String path = String.format("/photo/:/transcode?width=%d&height=%d&url=%s", width, height, Uri.encode(String.format("http://127.0.0.1:32400%s", thumb)));
		PlexHttpClient.get(server, path, new BinaryHttpResponseHandler() {
			@Override
			public void onFailure(int statusCode, Header[] headers, byte[] binaryData, Throwable error) {
				super.onFailure(statusCode, headers, binaryData, error);
				Logger.d("failed getting thumb: %s", statusCode);
			}

			@Override
			public void onSuccess(byte[] imageData) {
				InputStream is  = new ByteArrayInputStream(imageData);
				try {
					is.reset();
					Bitmap bitmap = BitmapFactory.decodeStream(is);
					if(bitmapHandler != null) {
						bitmapHandler.onSuccess(bitmap);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void writeToParcel(Parcel parcel, int i) {

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

  @Root(strict=false)
  static class Media {
    @ElementList(required=false, inline=true, entry="Part")
    public List<Part> parts = new ArrayList<Part>();
  }

  @Root(strict=false)
  static class Part {
    @Attribute
    public String id;
    @ElementList(required=false, inline=true, entry="Stream")
    public List<Stream> streams = new ArrayList<Stream>();
  }

  @Root(strict=false)
  static class Stream {
    @Attribute(required=false)
    public String id;
    @Attribute(required=false)
    public int streamType;
    @Attribute(required=false)
    public String language;
  }
}
