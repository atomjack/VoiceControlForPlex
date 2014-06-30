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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteScan {
	private static Gson gson = new Gson();

	private static AsyncHttpClient client = new AsyncHttpClient();
	private static Serializer serial = new Persister();

	public interface RefreshResourcesResponseHandler {
		void onSuccess();
		void onFailure(int statusCode);
	}

	public static void refreshResources(String authToken, final RefreshResourcesResponseHandler responseHandler) {
		refreshResources(authToken, responseHandler, false);
	}

	public static void refreshResources(String authToken) {
		refreshResources(authToken, null, true);
	}


	public static void refreshResources(String authToken, final RefreshResourcesResponseHandler responseHandler, boolean silent) {
		VoiceControlForPlexApplication.servers = new ConcurrentHashMap<String, PlexServer>();
		VoiceControlForPlexApplication.clients = new HashMap<String, PlexClient>();
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

					List<PlexServer> servers = new ArrayList<PlexServer>();
					for(final Device device : mediaContainer.devices) {
						if(device.lastSeenAt < System.currentTimeMillis()/1000 - (60*60*24))
							continue;
						if(device.provides.contains("server")) {

							PlexServer server = PlexServer.fromDevice(device);
							Logger.d("Device %s is a server, has %d connections", server.name, server.connections.size());
							servers.add(server);
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
					Preferences.put(Preferences.SAVED_CLIENTS, gson.toJson(VoiceControlForPlexApplication.clients));

					final int[] serversScanned = new int[1];
					serversScanned[0] = 0;
					final int numServers = servers.size();
					for(final PlexServer server : servers) {
						VoiceControlForPlexApplication.addPlexServer(server, new Runnable() {
							@Override
							public void run() {
              Logger.d("Done scanning %s", server.name);
              serversScanned[0]++;
              Logger.d("%d out of %d servers scanned", serversScanned[0], numServers);
              if(serversScanned[0] >= numServers && responseHandler != null)
                responseHandler.onSuccess();
							}
						});
					}





				} catch (Exception e) {
          if(responseHandler != null)
            responseHandler.onFailure(0);
				}
			}

			@Override
			public void onFailure(int statusCode, org.apache.http.Header[] headers, byte[] responseBody, java.lang.Throwable error) {
				Logger.d("Failure getting resources: %d", statusCode);
				error.printStackTrace();
				if(responseHandler != null)
					responseHandler.onFailure(statusCode);
			}
		});
	}




}
