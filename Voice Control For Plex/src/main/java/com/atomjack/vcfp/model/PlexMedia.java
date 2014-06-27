package com.atomjack.vcfp.model;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.handlers.BitmapHandler;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.loopj.android.http.BinaryHttpResponseHandler;

import org.apache.http.Header;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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

	public String getThumbUri(int width, int height) {
		String url = String.format("%s/photo/:/transcode/?width=%d&height=%d&url=%s", server.activeConnection.uri,
			width, height, Uri.encode(String.format("http://127.0.0.1%s", key)));
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
}
