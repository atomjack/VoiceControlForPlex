package com.atomjack.vcfp;

import android.content.SharedPreferences;
import android.content.res.Resources;

import com.atomjack.vcfp.model.Device;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexServer;
import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.util.HashMap;

public class RemoteScan {
	private SharedPreferences mPrefs;
	private Gson gson = new Gson();

	private static AsyncHttpClient client = new AsyncHttpClient();
	private static Serializer serial = new Persister();

	public RemoteScan(SharedPreferences prefs) {
		mPrefs = prefs;
	}

	public interface RefreshResourcesResponseHandler {
		void onSuccess();
		void onFailure(int statusCode);
	}

	public void refreshResources(String authToken, final RefreshResourcesResponseHandler responseHandler) {
		String url = String.format("https://plex.tv/pms/resources?%s=%s", PlexHeaders.XPlexToken, authToken);
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
					Logger.d("got %d devices", mediaContainer.devices.size());
					for(Device device : mediaContainer.devices) {
						if(device.lastSeenAt < System.currentTimeMillis()/1000 - (60*60*24))
							continue;
						if(device.provides.contains("server")) {

							PlexServer server = PlexServer.fromDevice(device);
							Logger.d("Device %s is a server", server.name);
							VoiceControlForPlexApplication.addPlexServer(server);

						} else if(device.provides.contains("player")) {
							Logger.d("Device %s is a player", device.name);
							PlexClient client = PlexClient.fromDevice(device);
							if(VoiceControlForPlexApplication.clients == null)
								VoiceControlForPlexApplication.clients = new HashMap<String, PlexClient>();
							if(!VoiceControlForPlexApplication.clients.containsKey(client.name)) {
								VoiceControlForPlexApplication.clients.put(client.name, client);
							}
						}
 					}
					mPrefs.edit().putString(Preferences.SAVED_CLIENTS, gson.toJson(VoiceControlForPlexApplication.clients));
					mPrefs.edit().commit();

					responseHandler.onSuccess();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onFailure(int statusCode, org.apache.http.Header[] headers, byte[] responseBody, java.lang.Throwable error) {
				Logger.d("Failure getting resources: %d", statusCode);
				error.printStackTrace();
				responseHandler.onFailure(statusCode);
//				responseHandler.onFailure(error);
			}
		});
	}




}
