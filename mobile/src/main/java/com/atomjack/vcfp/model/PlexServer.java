package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.atomjack.shared.Logger;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.QueryString;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.interfaces.ActiveConnectionHandler;
import com.atomjack.vcfp.interfaces.AfterTransientTokenRequest;
import com.atomjack.vcfp.interfaces.ServerTestHandler;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;
import com.google.gson.reflect.TypeToken;

import org.simpleframework.xml.Root;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import retrofit.Call;
import retrofit.Callback;
import retrofit.Response;
import retrofit.Retrofit;

@Root(strict=false)
public class PlexServer extends PlexDevice {

	public String sourceTitle;

	public List<String> movieSections = new ArrayList<String>();
	public List<String> tvSections = new ArrayList<String>();
	public List<String> musicSections = new ArrayList<String>();

  public int movieSectionsSearched = 0;
  public int tvSectionsSearched = 0;
  public int musicSectionsSearched = 0;

	public boolean owned = true;
	public String accessToken;

	public boolean local;

  public boolean isScanAllServer = false;

	public PlexServer() {
		super();
    connections = new ArrayList<Connection>();
	}

	public PlexServer(String _name) {
    super();
		name = _name;
		connections = new ArrayList<Connection>();
	}

  public static PlexServer getScanAllServer() {
    PlexServer s = new PlexServer(VoiceControlForPlexApplication.getInstance().getString(R.string.scan_all));
    s.machineIdentifier = "000000";
    s.isScanAllServer = true;
    return s;
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
    parcel.writeStringList(movieSections);
    parcel.writeStringList(tvSections);
    parcel.writeStringList(musicSections);
    parcel.writeInt(owned ? 1 : 0);
    parcel.writeString(sourceTitle);
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
    in.readStringList(movieSections);
    in.readStringList(tvSections);
    in.readStringList(musicSections);
    owned = in.readInt() == 1;
    sourceTitle = in.readString();
  }

	public static final Parcelable.Creator<PlexServer> CREATOR = new Parcelable.Creator<PlexServer>() {
		public PlexServer createFromParcel(Parcel in) {
			return new PlexServer(in);
		}
		public PlexServer[] newArray(int size) {
			return new PlexServer[size];
		}
	};

  public void findServerConnection(final ActiveConnectionHandler activeConnectionHandler) {
    findServerConnection(false, activeConnectionHandler);
  }

  // force == ignore active connection and scan for all connections. Defaults to false (use activeConnection, if it exists)
  public void findServerConnection(boolean force, final ActiveConnectionHandler activeConnectionHandler) {
    Calendar activeConnectionExpires = null;
    Connection activeConnection = null;
    if(!force) {
      try {
        HashMap<String, Calendar> activeConnectionExpiresList = VoiceControlForPlexApplication.getInstance().getActiveConnectionExpiresList();
        HashMap<String, Connection> activeConnectionList = VoiceControlForPlexApplication.getInstance().getActiveConnectionList();

        if (activeConnectionExpiresList.containsKey(machineIdentifier))
          activeConnectionExpires = activeConnectionExpiresList.get(machineIdentifier);

        if (activeConnectionList.containsKey(machineIdentifier))
          activeConnection = activeConnectionList.get(machineIdentifier);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    // If we have an active connection and it has not expired yet, AND (the active connection is on the same network OR it is an external IP),
    // return the active connection. Otherwise, scan for them.
    // If the active connection is an external IP, we obviously don't care if it's on a different network.
    if(activeConnectionExpires != null && !activeConnectionExpires.before(Calendar.getInstance()) &&
            (activeConnection.isOnSameNetwork() || !activeConnection.isPrivateV4Address())) {
      activeConnectionHandler.onSuccess(activeConnection);
    } else {
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm:ss a");
      logger.d("finding server connection for %s, current active connection expires: %s, now: %s",
              name,
              activeConnectionExpires != null ? simpleDateFormat.format(activeConnectionExpires.getTime()) : null,
              simpleDateFormat.format(Calendar.getInstance().getTime()));
      findServerConnection(0, activeConnectionHandler);
    }
  }

  private void findServerConnection(final int connectionIndex, final ActiveConnectionHandler activeConnectionHandler) {
    final Connection connection = connections.get(connectionIndex);
    // If this connection is a lan IP address and is not on the same subnet as the device, skip testing this connection
    if(connection.isPrivateV4Address() && !connection.isOnSameNetwork()) {
      logger.d("Connection %s for server %s is private but not on the same network. Skipping.", connection.address, name);
      int newConnectionIndex = connectionIndex + 1;
      if (connections.size() <= newConnectionIndex) {
        activeConnectionHandler.onFailure(0);
      } else
        findServerConnection(newConnectionIndex, activeConnectionHandler);
      return;
    }
    testServerConnection(connection, new ServerTestHandler() {
      @Override
      public void onFinish(int statusCode, boolean available) {
        if (available) {
          // This connection replied, so let's use it
          Connection activeConnection = connections.get(connectionIndex);
          Logger.d("Found connection for %s: %s", name, activeConnection);
          Calendar activeConnectionExpires = Calendar.getInstance();
          activeConnectionExpires.add(Calendar.HOUR_OF_DAY, 1);
          // Now save this connection and expiration
          VoiceControlForPlexApplication.getInstance().saveActiveConnection(PlexServer.this, activeConnection, activeConnectionExpires);
          VoiceControlForPlexApplication.servers.put(name, PlexServer.this);
          Type serverType = new TypeToken<ConcurrentHashMap<String, PlexServer>>(){}.getType();
          VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SAVED_SERVERS, VoiceControlForPlexApplication.gsonWrite.toJson(VoiceControlForPlexApplication.servers, serverType));
          activeConnectionHandler.onSuccess(activeConnection);
        } else {
          int newConnectionIndex = connectionIndex + 1;
          Logger.d("Not available, new connection index: %d", newConnectionIndex);
          if (connections.size() <= newConnectionIndex) {
            activeConnectionHandler.onFailure(statusCode);
          } else
            findServerConnection(newConnectionIndex, activeConnectionHandler);
        }
      }
    });
  }

	private void testServerConnection(final Connection connection, final ServerTestHandler handler) {
    PlexHttpClient.PlexHttpService service = PlexHttpClient.getService(connection, 2);
    Call<MediaContainer> call = service.getMediaContainer("", accessToken);
    call.enqueue(new Callback<MediaContainer>() {
      @Override
      public void onResponse(Response<MediaContainer> response, Retrofit retrofit) {
        Logger.d("Testing connection %s got code: %d", connection, response.code());
        if(response.code() == 200) {
          Logger.d("%s success", connection.uri);
          handler.onFinish(response.code(), true);
        } else {
          handler.onFinish(response.code(), false);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        Logger.d("%s failed", connection.uri);
        handler.onFinish(0, false);
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

  public String buildURL(Connection connection, String path) {
    String url = String.format("%s%s", connection.uri, path);
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

  public void localPlay(final PlexMedia media, final boolean resumePlayback, final String containerKey, final String transientToken) {
    findServerConnection(new ActiveConnectionHandler() {
      @Override
      public void onSuccess(Connection connection) {
        QueryString qs = new QueryString("machineIdentifier", machineIdentifier);
        Logger.d("machine id: %s", machineIdentifier);
        qs.add("key", media.key);
        Logger.d("key: %s", media.key);
        qs.add("port", connection.port);
        Logger.d("port: %s", connection.port);
        qs.add("address", connection.address);
        Logger.d("address: %s", connection.address);

        if(containerKey != null)
          qs.add("containerKey", containerKey);

        if((VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false) || resumePlayback) && media.viewOffset != null)
          qs.add("viewOffset", media.viewOffset);
        if(transientToken != null)
          qs.add("token", transientToken);
        if(accessToken != null)
          qs.add(PlexHeaders.XPlexToken, accessToken);
        String url = String.format("http://127.0.0.1:32400/player/playback/playMedia?%s", qs);
        logger.d("Playback url: %s ", url);
        PlexHttpClient.get("http://127.0.0.1:32400", String.format("player/playback/playMedia?%s", qs), new PlexHttpResponseHandler()
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

      @Override
      public void onFailure(int statusCode) {

      }
    });
  }

  public String getRandomMusicSection() {
    return musicSections.get(new Random().nextInt(musicSections.size()));
  }

  public String getRandomTvSection() {
    return tvSections.get(new Random().nextInt(tvSections.size()));
  }

  public String getRandomMovieSection() {
    return movieSections.get(new Random().nextInt(movieSections.size()));
  }
}
