package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.ServerFindHandler;
import com.atomjack.vcfp.ServerTestHandler;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.simpleframework.xml.Root;

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
		parcel.writeTypedList(connections);
	}

	public PlexServer(Parcel in) {
		this();
		name = in.readString();
		port = in.readString();
		version = in.readString();
		product = in.readString();
		address = in.readString();
		accessToken = in.readString();
		in.readTypedList(connections, Connection.CREATOR);
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
		else
			findServerConnection(0, handler);
	}

	private void findServerConnection(final int connectionIndex, final ServerFindHandler handler) {
		Logger.d("findServerConnection: index %d", connectionIndex);
		final Connection connection = connections.get(connectionIndex);
		testServerConnection(connection, new ServerTestHandler() {
			@Override
			public void onFinish(boolean available) {
				if(available) {
					// This connection replied, so let's use it
					activeConnection = connections.get(connectionIndex);
					handler.onSuccess();
				} else {
					int newConnectionIndex = connectionIndex + 1;
					// TODO: Fix this
					if(connections.size() <= newConnectionIndex)
						handler.onFailure();
					else
						findServerConnection(newConnectionIndex, handler);
				}
			}
		});
	}

	private void testServerConnection(final Connection connection, final ServerTestHandler handler) {
		AsyncHttpClient httpClient = new AsyncHttpClient();
		Logger.d("testServerConnection: fetching %s", connection.uri);
		// Set timeout to 2 seconds, we don't want this to take too long
		httpClient.setTimeout(2000);
		if(accessToken != null)
			httpClient.addHeader(PlexHeaders.XPlexToken, accessToken);
		httpClient.get(connection.uri, new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
				Logger.d("%s success", connection.uri);
				handler.onFinish(true);
			}

			@Override
			public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
				Logger.d("%s failed", connection.uri);
				handler.onFinish(false);
			}
		});
	}
}
