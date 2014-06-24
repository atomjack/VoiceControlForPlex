package com.atomjack.vcfp.net;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.Pin;
import com.atomjack.vcfp.model.PlexError;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexUser;
import com.atomjack.vcfp.model.PlexVideo;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.BinaryHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class PlexHttpClient
{
  private static AsyncHttpClient client = new AsyncHttpClient();
  private static Serializer serial = new Persister();

	public static void get(PlexServer server, String path, final PlexHttpMediaContainerHandler responseHandler) {
		if(server.activeConnection == null) {
			responseHandler.onFailure(new Throwable());
			return;
		}
		String url = String.format("%s%s", server.activeConnection.uri, path);
		if(server.accessToken != null)
			url += String.format("%s%s=%s", (url.contains("?") ? "&" : "?"), PlexHeaders.XPlexToken, server.accessToken);
    Logger.d("Fetching %s", url);
    client.get(url, new RequestParams(), new AsyncHttpResponseHandler() {
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

	public static void get(Context context, String url, Header[] headers, final PlexHttpResponseHandler responseHandler) {
		Logger.d("Fetching %s", url);
		client.get(context, url, headers, new RequestParams(), new AsyncHttpResponseHandler() {
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

			@Override
			public void onFailure(int statusCode, org.apache.http.Header[] headers, byte[] responseBody, java.lang.Throwable error) {
				responseHandler.onFailure(error);
			}
		});
	}

	public static void get(String url, final PlexHttpResponseHandler responseHandler) {
		Logger.d("Fetching %s", url);
		client.get(url, new RequestParams(), new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
				PlexResponse r = new PlexResponse();
				try {
					r = serial.read(PlexResponse.class, new String(responseBody, "UTF-8"));
				} catch (Exception e) {
					Logger.e("Exception parsing response: %s", e.toString());
				}
				if(responseHandler != null)
					responseHandler.onSuccess(r);
			}

			@Override
			public void onFailure(int statusCode, org.apache.http.Header[] headers, byte[] responseBody, java.lang.Throwable error) {
				if(responseHandler != null)
					responseHandler.onFailure(error);
			}
		});
	}

	public static void getPinCode(Context context, Header[] headers, final PlexPinResponseHandler responseHandler) {
		client.post(context, "https://plex.tv:443/pins.xml", headers, new RequestParams(), "text/xml", new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {

				Pin pin = new Pin();
				try {
					pin = serial.read(Pin.class, new String(responseBody, "UTF-8"));
				} catch (Exception e) {
					Logger.e("Exception parsing response: %s", e.toString());
				}
				responseHandler.onSuccess(pin);
			}

			@Override
			public void onFailure(int statusCode, org.apache.http.Header[] headers, byte[] responseBody, java.lang.Throwable error) {
				responseHandler.onFailure(error);
			}
		});
	}

	public static void signin(Context context, String authToken, Header[] headers, String contentType, final PlexHttpUserHandler responseHandler) {
		signin(context, null, null, authToken, headers, contentType, responseHandler);
	}

	public static void signin(Context context, String username, String password, Header[] headers, String contentType, final PlexHttpUserHandler responseHandler) {
		signin(context, username, password, null, headers, contentType, responseHandler);
	}

	public static void signin(Context context, String username, String password, String authToken, Header[] headers, String contentType, final PlexHttpUserHandler responseHandler) {
		if(username != null && password != null)
			client.setBasicAuth(username, password);
		String url = "https://plex.tv/users/sign_in.xml";
		if(authToken != null)
			url += "?auth_token=" + authToken;
		client.post(context, url, headers, new RequestParams(), contentType, new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
				Logger.d("signin success: %d", statusCode);
				PlexUser u = new PlexUser();
				try {
					u = serial.read(PlexUser.class, new String(responseBody, "UTF-8"));
				} catch (Exception e) {
					Logger.e("Exception parsing response: %s", e.toString());
				}
				responseHandler.onSuccess(u);
			}

			@Override
			public void onFailure(int statusCode, org.apache.http.Header[] headers, byte[] responseBody, java.lang.Throwable error) {
				if(responseBody != null)
					Logger.d("body: %s", new String(responseBody));
				PlexError er = new PlexError();
				try {
				  er = serial.read(PlexError.class, new String(responseBody, "UTF-8"));
				} catch (Exception e) {
					Logger.e("Exception parsing response: %s", e.toString());
				}
				Logger.d("error: %s", er.errors);
				responseHandler.onFailure(statusCode, er);
			}
		});
	}

	@SuppressWarnings("deprecation")
	public static void setThumb(PlexTrack track, final RelativeLayout layout) {
    if(track.thumb != null && !track.thumb.equals("")) {
      try {
				String url = String.format("http://%s:%s%s", track.server.address, track.server.port, track.thumb);
				if(track.server.accessToken != null)
					url += String.format("?%s=%s", PlexHeaders.XPlexToken, track.server.accessToken);
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
						if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
							layout.setBackground(d);
						else
							layout.setBackgroundDrawable(d);
          }
        });
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
  }

	@SuppressWarnings("deprecation")
  public static void setThumb(PlexVideo video, final RelativeLayout layout) {
    if(!video.thumb.equals("")) {
      try {
				String url = String.format("http://%s:%s%s", video.server.activeConnection.address, video.server.activeConnection.port, video.thumb);
				if(video.server.accessToken != null)
					url += String.format("?%s=%s", PlexHeaders.XPlexToken, video.server.accessToken);
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
						if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
							layout.setBackground(d);
						else
							layout.setBackgroundDrawable(d);
          }
        });
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
  }
}

