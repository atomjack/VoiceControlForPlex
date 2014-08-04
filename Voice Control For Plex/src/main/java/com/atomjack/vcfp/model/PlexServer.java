package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.atomjack.vcfp.AfterTransientTokenRequest;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.QueryString;
import com.atomjack.vcfp.ServerFindHandler;
import com.atomjack.vcfp.ServerTestHandler;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.apache.http.Header;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.List;

@Root(strict=false)
public class PlexServer extends PlexDevice {

	public String sourceTitle;

	public List<String> movieSections = new ArrayList<String>();
	public List<String> tvSections = new ArrayList<String>();
	public List<String> musicSections = new ArrayList<String>();

  public int movieSectionsSearched = 0;
  public int showSectionsSearched = 0;
  public int musicSectionsSearched = 0;

	public boolean owned = true;
	public String accessToken;

	public Connection activeConnection;

	public boolean local;

	public PlexServer() {
		connections = new ArrayList<Connection>();
	}
	public PlexServer(String _name) {
		name = _name;
		connections = new ArrayList<Connection>();
	}

	public static PlexServer fromDevice(Device device) {
		Logger.d("Creating server %s", device.name);
		PlexServer server = new PlexServer(device.name);
		server.sourceTitle = device.sourceTitle;
		server.connections = device.connections;
		server.address = "";
		if(!device.owned) {
			Logger.d("Not owned, has %d connections.", device.connections.size());
			for(Connection connection : device.connections) {
				Logger.d("Connection %s: %s", connection.address, connection.local);
				if(!connection.local) {
					server.address = connection.address;
					server.port = connection.port;
					break;
				}
			}
		} else {
			if(server.connections != null && server.connections.size() > 0) {
				server.address = server.connections.get(0).address;
				server.port = server.connections.get(0).port;
			}
		}
		server.machineIdentifier = device.clientIdentifier;
		server.owned = device.owned;
		server.product = device.product;
		server.accessToken = device.accessToken;
		return server;
	}

	public void addMovieSection(String key) {
		if(!movieSections.contains(key)) {
			movieSections.add(key);
		}
	}

	public void addTvSection(String key) {
		if(!tvSections.contains(key)) {
			tvSections.add(key);
		}
	}
	
	public void addMusicSection(String key) {
		if(!musicSections.contains(key)) {
			musicSections.add(key);
		}
	}

	@Override
	public String toString() {
		String output = "";
		output += "Name: " + name + "\n";
		output += "IP Address: " + address + "\n";
		if(connections != null) {
			output += "Connections: \n";
			for (Connection c : connections) {
				output += String.format("%s (%s)\n", c.uri, c.local);
			}
		}
		return output;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel parcel, int i) {
		parcel.writeString(name);
		parcel.writeString(port);
		parcel.writeString(version);
		parcel.writeString(product);
		parcel.writeString(address);
		parcel.writeString(accessToken);
    parcel.writeString(machineIdentifier);
		parcel.writeTypedList(connections);
		parcel.writeParcelable(activeConnection, i);
	}

	public PlexServer(Parcel in) {
		this();
		name = in.readString();
		port = in.readString();
		version = in.readString();
		product = in.readString();
		address = in.readString();
		accessToken = in.readString();
    machineIdentifier = in.readString();
		in.readTypedList(connections, Connection.CREATOR);
		activeConnection = in.readParcelable(Connection.class.getClassLoader());
	}

	public static final Parcelable.Creator<PlexServer> CREATOR = new Parcelable.Creator<PlexServer>() {
		public PlexServer createFromParcel(Parcel in) {
			return new PlexServer(in);
		}
		public PlexServer[] newArray(int size) {
			return new PlexServer[size];
		}
	};


	public void findServerConnection(final ServerFindHandler handler) {
		// If this server already has an active connection, just return it without trying it again.
		if(activeConnection != null)
			handler.onSuccess();
		else {
			// If this server has no connections, create one with the server's address and port.
			// This can happen if a user was using the app before multiple connections were supported.
			if(connections.size() == 0)
				connections.add(new Connection("http", address, port));
			findServerConnection(0, handler);
		}
	}

	private void findServerConnection(final int connectionIndex, final ServerFindHandler handler) {
		Logger.d("findServerConnection: index %d", connectionIndex);
		final Connection connection = connections.get(connectionIndex);
		testServerConnection(connection, new ServerTestHandler() {
			@Override
			public void onFinish(int statusCode, boolean available) {
				if(available) {
					// This connection replied, so let's use it
					activeConnection = connections.get(connectionIndex);
					handler.onSuccess();
				} else {
					int newConnectionIndex = connectionIndex + 1;
					if(connections.size() <= newConnectionIndex)
						handler.onFailure(statusCode);
					else
						findServerConnection(newConnectionIndex, handler);
				}
			}
		});
	}

	private void testServerConnection(final Connection connection, final ServerTestHandler handler) {
		AsyncHttpClient httpClient = new AsyncHttpClient();

		// Set timeout to 2 seconds, we don't want this to take too long
		httpClient.setTimeout(2000);
		String url = connection.uri;
		if(accessToken != null)
			url += String.format("/?%s=%s", PlexHeaders.XPlexToken, accessToken);
		Logger.d("testServerConnection: fetching %s", connection.uri);
		httpClient.get(url, new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
				Logger.d("%s success", connection.uri);
				handler.onFinish(statusCode, true);
			}

			@Override
			public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
				// unauthorized: 401
				// timeout: 0
				Logger.d("Status Code: %d", statusCode);
				Logger.d("%s failed", connection.uri);
				handler.onFinish(statusCode, false);
			}
		});
	}

	public void requestTransientAccessToken(final AfterTransientTokenRequest onFinish) {
		String path = "/security/token?type=delegation&scope=all";
		PlexHttpClient.get(this, path, new PlexHttpMediaContainerHandler() {
			@Override
			public void onSuccess(MediaContainer mediaContainer) {
				onFinish.success(mediaContainer.token);
			}

			@Override
			public void onFailure(Throwable error) {
				error.printStackTrace();
				onFinish.failure();
			}
		});
	}

  public String buildURL(String path) {
    String url = String.format("%s%s", activeConnection.uri, path);
    if(accessToken != null)
      url += String.format("%s%s=%s", (url.contains("?") ? "&" : "?"), PlexHeaders.XPlexToken, accessToken);
    return url;
  }

  public void localPlay(PlexMedia media, boolean resumePlayback, String transientToken) {
    localPlay(media, resumePlayback, null, transientToken);
  }

  public void localPlay(PlexMedia media, String containerKey, boolean resumePlayback) {
    localPlay(media, resumePlayback, containerKey, null);
  }

  public void localPlay(PlexMedia media, boolean resumePlayback, String containerKey, String transientToken) {
    QueryString qs = new QueryString("machineIdentifier", machineIdentifier);
    Logger.d("machine id: %s", machineIdentifier);
    qs.add("key", media.key);
    Logger.d("key: %s", media.key);
    qs.add("port", activeConnection.port);
    Logger.d("port: %s", activeConnection.port);
    qs.add("address", activeConnection.address);
    Logger.d("address: %s", activeConnection.address);

    if(containerKey != null)
      qs.add("containerKey", containerKey);

    if((VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false) || resumePlayback) && media.viewOffset != null)
      qs.add("viewOffset", media.viewOffset);
    if(transientToken != null)
      qs.add("token", transientToken);
    if(accessToken != null)
      qs.add(PlexHeaders.XPlexToken, accessToken);
    String url = String.format("http://127.0.0.1:32400/player/playback/playMedia?%s", qs);
    Logger.d("[PlexServer] Playback url: %s ", url);
    PlexHttpClient.get(url, new PlexHttpResponseHandler()
    {
      @Override
      public void onSuccess(PlexResponse r)
      {
      }

      @Override
      public void onFailure(Throwable error) {
//        feedback.e(getResources().getString(R.string.got_error), error.getMessage());
      }
    });
  }
}
