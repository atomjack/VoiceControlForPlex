package com.atomjack.vcfp.net;

import android.content.res.Resources;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.Pin;
import com.atomjack.vcfp.model.PlexError;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexUser;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.BinaryHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;

public class PlexHttpClient
{
  private static AsyncHttpClient client = new AsyncHttpClient();
  private static Serializer serial = new Persister();

  public static AsyncHttpClient getClient() {
    return client;
  }

	public static void get(PlexServer server, String path, final BinaryHttpResponseHandler responseHandler) {
		String url = String.format("%s%s", server.activeConnection.uri, path);
		if(server.accessToken != null)
			url += String.format("%s%s=%s", (url.contains("?") ? "&" : "?"), PlexHeaders.XPlexToken, server.accessToken);
		Logger.d("url: %s", url);
		client.get(url, responseHandler);
	}

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

  public static void get(String url, final Header[] theseHeaders, final PlexHttpResponseHandler responseHandler) {
    Logger.d("Fetching %s", url);
    addHeaders(theseHeaders);
    client.get(url, new RequestParams(), new AsyncHttpResponseHandler() {
      @Override
      public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
        // Remove the headers we just added
        removeHeaders(theseHeaders);
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
        removeHeaders(theseHeaders);
        if(responseHandler != null)
          responseHandler.onFailure(error);
      }
    });
  }

  private static void addHeaders(Header[] headers) {
    for(Header header : headers) {
      client.addHeader(header.getName(), header.getValue());
    }
  }

  private static void removeHeaders(Header[] headers) {
    for(Header header : headers) {
      client.removeHeader(header.getName());
    }
  }

  public static void get(String url, final Header[] theseHeaders, final PlexHttpMediaContainerHandler responseHandler) {
    Logger.d("Fetching %s", url);
    addHeaders(theseHeaders);
    client.get(url, new RequestParams(), new AsyncHttpResponseHandler() {
      @Override
      public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
        removeHeaders(theseHeaders);
        MediaContainer mediaContainer = new MediaContainer();

        try {
          mediaContainer = serial.read(MediaContainer.class, new String(responseBody, "UTF-8"));
        } catch (Resources.NotFoundException e) {
          e.printStackTrace();
        } catch (Exception e) {
          e.printStackTrace();
        }

        responseHandler.onSuccess(mediaContainer);
      }

      @Override
      public void onFailure(int statusCode, org.apache.http.Header[] headers, byte[] responseBody, java.lang.Throwable error) {
        removeHeaders(theseHeaders);
        if(responseHandler != null)
          responseHandler.onFailure(error);
      }
    });
  }

	public static void get(String url, final PlexHttpResponseHandler responseHandler) {
		Logger.d("Fetching %s", url);
		client.get(url, new RequestParams(), new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
        Logger.d("Response: %s", new String(responseBody));
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

	public static void getPinCode(final Header[] theseHeaders, final PlexPinResponseHandler responseHandler) {
    addHeaders(theseHeaders);
		client.post("https://plex.tv:443/pins.xml", new RequestParams(), new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
        removeHeaders(theseHeaders);

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
        removeHeaders(theseHeaders);
				responseHandler.onFailure(error);
			}
		});
	}

	public static void signin(String authToken, Header[] headers, final PlexHttpUserHandler responseHandler) {
		signin(null, null, authToken, headers, responseHandler);
	}

	public static void signin(String username, String password, Header[] headers, final PlexHttpUserHandler responseHandler) {
		signin(username, password, null, headers, responseHandler);
	}

	public static void signin(String username, String password, String authToken, final Header[] theseHeaders, final PlexHttpUserHandler responseHandler) {
		if(username != null && password != null)
			client.setBasicAuth(username, password);
		String url = "https://plex.tv/users/sign_in.xml";
		if(authToken != null)
			url += "?auth_token=" + authToken;
    addHeaders(theseHeaders);
		client.post(url, new RequestParams(), new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
        removeHeaders(theseHeaders);
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
        removeHeaders(theseHeaders);
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

  public static PlexResponse getSync(String url) {
    String body = getSyncBody(url, null);
    PlexResponse r;
    try {
      r = serial.read(PlexResponse.class, body);
    } catch (Exception e) {
      Logger.e("Exception parsing response: %s", e.toString());
      return null;
    }
    return r;
  }

  public static MediaContainer getSync(String url, Header[] headers) {
    String body = getSyncBody(url, headers);
    MediaContainer mc;
    try {
      mc = serial.read(MediaContainer.class, body);
    } catch (Exception e) {
      Logger.e("Exception parsing response: %s", e.toString());
      return null;
    }
    return mc;
  }

  public static byte[] getSyncBytes(String url) throws SocketTimeoutException {
    try {
      HttpGet get = new HttpGet(url);
      HttpParams httpParameters = new BasicHttpParams();
      int timeoutConnection = 3000;
      HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
      int timeoutSocket = 5000;
      HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);


      HttpClient httpclient = new DefaultHttpClient(httpParameters);

      HttpResponse response = httpclient.execute(get);
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getEntity().writeTo(out);
        out.close();
        return out.toByteArray();
      } else {
        //Closes the connection.
        response.getEntity().getContent().close();
        throw new IOException(statusLine.getReasonPhrase());
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return null;
  }

  public static String getSyncBody(String url, Header[] headers) {
    try {
      HttpClient httpclient = new DefaultHttpClient();
      HttpGet get = new HttpGet(url);
      if(headers != null) {
        get.setHeaders(headers);
      }
      HttpResponse response = httpclient.execute(get);
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getEntity().writeTo(out);
        out.close();
        String body = out.toString();
        return body;
      } else {
        //Closes the connection.
        response.getEntity().getContent().close();
        throw new IOException(statusLine.getReasonPhrase());
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return null;
  }
}

