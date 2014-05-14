package com.atomjack.vcfp.net;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.ScrollView;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.BinaryHttpResponseHandler;
import com.loopj.android.http.RequestParams;

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
    });
  }

  public static void get(String url, RequestParams params, final PlexHttpResponseHandler responseHandler) {
    Logger.d("Fetching %s", url);
    client.get(url, params, new AsyncHttpResponseHandler() {
      @Override
      public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
        PlexResponse r = new PlexResponse();
        try {
          r = serial.read(PlexResponse.class, new String(responseBody, "UTF-8"));
        } catch (Exception e) {
          Logger.e("Exception parsing response: %s", e.toString());
        }
        responseHandler.onSuccess(r);
      }
    });
  }

  public static void setThumb(PlexTrack track, final ImageView imageView) {
    if(track.getThumb() != null && !track.getThumb().equals("")) {
      try {
        final String url = "http://" + track.getServer().getAddress() + ":" + track.getServer().getPort() + track.getThumb();
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
    if(!video.getThumb().equals("")) {
      try {
        final String url = "http://" + video.getServer().getAddress() + ":" + video.getServer().getPort() + video.getThumb();
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

