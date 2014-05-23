package com.atomjack.vcfplib.net;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.ScrollView;

import com.atomjack.vcfplib.Logger;
import com.atomjack.vcfplib.model.MediaContainer;
import com.atomjack.vcfplib.model.PlexResponse;
import com.atomjack.vcfplib.model.PlexTrack;
import com.atomjack.vcfplib.model.PlexVideo;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.BinaryHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.client.HttpResponseException;
import org.apache.http.conn.HttpHostConnectException;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PlexHttpClient
{
  private static AsyncHttpClient client = new AsyncHttpClient();
  private static Serializer serial = new Persister();

  public static void get(String url, RequestParams params, final PlexHttpMediaContainerHandler responseHandler) {
    Logger.d("Fetching %s", url);
    client.get(url, params, new AsyncHttpResponseHandler() {
      @Override
      public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
        try {
          MediaContainer mediaContainer = new MediaContainer();

          try {
            mediaContainer = serial.read(MediaContainer.class, new String(responseBody, "UTF-8"));
          } catch (Resources.NotFoundException e) {
            e.printStackTrace();
          } catch (Exception e) {
            e.printStackTrace();
          }

          responseHandler.onSuccess(mediaContainer);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

			@Override
			public void onFailure(int statusCode, org.apache.http.Header[] headers, byte[] responseBody, java.lang.Throwable error) {
				responseHandler.onFailure(error);
			}
    });
  }

  public static void get(String url, RequestParams params, final PlexHttpResponseHandler responseHandler) {
    Logger.d("Fetching %s", url);
    client.get(url, params, new AsyncHttpResponseHandler() {
      @Override
      public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
				Logger.d("GET SUCCESS");
        PlexResponse r = new PlexResponse();
        try {
          r = serial.read(PlexResponse.class, new String(responseBody, "UTF-8"));
        } catch (Exception e) {
          Logger.e("Exception parsing response: %s", e.toString());
        }
        responseHandler.onSuccess(r);
      }

			@Override
			public void onFailure(int statusCode, org.apache.http.Header[] headers, byte[] responseBody, java.lang.Throwable error) {
				responseHandler.onFailure(error);
			}
		});
  }

  public static void setThumb(PlexTrack track, final ImageView imageView) {
    if(track.thumb != null && !track.thumb.equals("")) {
      try {
        final String url = "http://" + track.server.address + ":" + track.server.port + track.thumb;
        Logger.d("Fetching thumb: %s", url);
        AsyncHttpClient httpClient = new AsyncHttpClient();
        httpClient.get(url, new BinaryHttpResponseHandler() {
          @Override
          public void onSuccess(byte[] imageData) {
            InputStream is  = new ByteArrayInputStream(imageData);
            try {
              is.reset();
            } catch (IOException e) {
              e.printStackTrace();
            }
            Drawable d = Drawable.createFromStream(is, "thumb");
            d.setAlpha(80);
            imageView.setImageDrawable(d);
          }
        });
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static void setThumb(PlexVideo video, final ScrollView layout) {
    if(!video.thumb.equals("")) {
      try {
        final String url = "http://" + video.server.address + ":" + video.server.port + video.thumb;
        Logger.d("Fetching Video Thumb: %s", url);
        AsyncHttpClient httpClient = new AsyncHttpClient();
        httpClient.get(url, new BinaryHttpResponseHandler() {
          @Override
          public void onSuccess(byte[] imageData) {
            InputStream is  = new ByteArrayInputStream(imageData);
            try {
              is.reset();
            } catch (IOException e) {
              e.printStackTrace();
            }
            Drawable d = Drawable.createFromStream(is, "thumb");
            d.setAlpha(80);
            layout.setBackground(d);
          }
        });
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
  }
}

