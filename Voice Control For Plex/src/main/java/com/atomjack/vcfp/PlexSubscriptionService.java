package com.atomjack.vcfp;

import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.Timeline;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlexSubscriptionService extends Service {

	public static final String ACTION_SUBSCRIBE = "com.atomjack.vcfp.action_subscribe";
	public static final String ACTION_SUBSCRIBED = "com.atomjack.vcfp.action_subscribed";
	public static final String ACTION_UNSUBSCRIBE = "com.atomjack.vcfp.action_unsubscribe";

	public static final String ACTION_UNSUBSCRIBED = "com.atomjack.vcfp.action_unsubscribed";
	public static final String ACTION_BROADCAST = "com.atomjack.vcfp.action_broadcast";
	public static final String ACTION_MESSAGE = "com.atomjack.vcfp.action_message";

	public static final String EXTRA_CLASS = "com.atomjack.vcfp.extra_class";
	public static final String EXTRA_CLIENT = "com.atomjack.vcfp.extra_client";
	public static final String EXTRA_TIMELINES = "com.atomjack.vcfp.extra_timelines";

	private static final int SUBSCRIBE_INTERVAL = 30000; // Send subscribe message every 30 seconds to keep us alive
	private Handler handler = new Handler();

	PlexClient mClient; // the client we are subscribing to
	private Gson gsonWrite = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriSerializer())
					.create();
	private Gson gsonRead = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriDeserializer())
					.create();

	private ArrayList<Class> mSubscribers; // Which classes are using this subscription
	private static Serializer serial = new Persister();

	private int commandId = 0;
	private int subscriptionPort = 59409;
	private boolean subscribed = false;
	private boolean subscriptionHasStarted = false;
	private ServerSocket serverSocket;
	Thread serverThread = null;
	Handler updateConversationHandler;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(final Intent intent, int flags, int startId) {
		Logger.d("PlexSubscription onStartCommand");

		if(mSubscribers == null)
			mSubscribers = new ArrayList<Class>();

		if(intent.getAction().equals(ACTION_SUBSCRIBE)) {
			PlexClient client = gsonRead.fromJson(intent.getStringExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT), PlexClient.class);
			Logger.d("client to subscribe to: %s", client.name);
			if (mClient == null) {
				mClient = client;

				Class subscriber = (Class) intent.getSerializableExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLASS);
				mSubscribers.add(subscriber);

				Logger.d("subscribed: %s", subscribed);
				if(!subscribed) {
					startSubscription(subscriber);
				}
			} else {
				Logger.d("mClient is not null");
				// TODO: Handle another activity
			}
		} else if(intent.getAction().equals(ACTION_UNSUBSCRIBE) && mClient != null) {
			unsubscribe((Class)intent.getSerializableExtra(EXTRA_CLASS), new Runnable() {
				@Override
				public void run() {
					Intent i = new Intent(ACTION_BROADCAST);
					i.setAction(ACTION_UNSUBSCRIBED);
					i.putExtra(EXTRA_CLASS, intent.getSerializableExtra(EXTRA_CLASS));
					LocalBroadcastManager.getInstance(PlexSubscriptionService.this).sendBroadcast(i);
				}
			});
		}
		return Service.START_NOT_STICKY;
	}

	private void unsubscribe(final Class subscriber, final Runnable onFinish) {
		if(mSubscribers.contains(subscriber))
			mSubscribers.remove(subscriber);

		if(mSubscribers.size() == 0) {
			QueryString qs = new QueryString("commandID", String.valueOf(commandId));
			Logger.d("mClient: %s", mClient);
			Header[] headers = {
							new BasicHeader(PlexHeaders.XPlexClientIdentifier, Preferences.getUUID()),
							new BasicHeader(PlexHeaders.XPlexDeviceName, getString(R.string.app_name)),
							new BasicHeader(PlexHeaders.XPlexTargetClientIdentifier, mClient.machineIdentifier)
			};
			PlexHttpClient.get(getApplicationContext(), String.format("http://%s:%s/player/timeline/unsubscribe?%s", mClient.address, mClient.port, qs), headers, new PlexHttpResponseHandler() {
				@Override
				public void onSuccess(PlexResponse response) {
					Logger.d("Unsubscribed");
					subscribed = false;
					commandId++;
					mClient = null;

					if (onFinish != null)
						onFinish.run();
				}

				@Override
				public void onFailure(Throwable error) {
					// TODO: Handle failure here?
					Logger.d("failure unsubscribing");
				}
			});
		} else if(onFinish != null) {
			onFinish.run();
		}
	}

	private synchronized void startSubscription(final Class subscriber) {
		Logger.d("startSubscription: %s", updateConversationHandler);
		if(updateConversationHandler == null) {
			updateConversationHandler = new Handler();
		}
		ServerThread thread = new ServerThread();
		thread.onReady(new Runnable() {
			@Override
			public void run() {
				Logger.d("subscribing");
				subscribe(subscriber);
			}
		});


		serverThread = new Thread(thread);
		serverThread.start();
	}

	private void subscribe(final Class subscriber) {
		Logger.d("subscribe()");
		QueryString qs = new QueryString("port", String.valueOf(subscriptionPort));
		qs.add("commandID", String.valueOf(commandId));
		qs.add("protocol", "http");

		Header[] headers = {
      new BasicHeader(PlexHeaders.XPlexClientIdentifier, Preferences.getUUID()),
      new BasicHeader(PlexHeaders.XPlexDeviceName, getString(R.string.app_name))
		};
		PlexHttpClient.get(getApplicationContext(), String.format("http://%s:%s/player/timeline/subscribe?%s", mClient.address, mClient.port, qs), headers, new PlexHttpResponseHandler() {
			@Override
			public void onSuccess(PlexResponse response) {
				Logger.d("Subscribed");
				commandId++;
				subscribed = true;

        // If the subscriber is null, this is being called as part of the subscription heartbeat
				if(subscriber != null) {
					// Send the Activity that initiated this subscription a message that we are subscribed.
					Intent intent = new Intent(ACTION_BROADCAST);
					intent.setAction(ACTION_SUBSCRIBED);
					intent.putExtra(EXTRA_CLASS, subscriber);
					LocalBroadcastManager.getInstance(PlexSubscriptionService.this).sendBroadcast(intent);

					// Start the heartbeat subscription (so the server knows we're still here)
					handler.postDelayed(subscriptionHeartbeat, SUBSCRIBE_INTERVAL);
				}
			}

			@Override
			public void onFailure(Throwable error) {
				error.printStackTrace();
			}
		});
	}

	private Runnable subscriptionHeartbeat = new Runnable() {
		@Override
		public void run() {
			if(subscribed) {
				subscribe(null);
				handler.postDelayed(subscriptionHeartbeat, SUBSCRIBE_INTERVAL);
			}
		}
	};
	class ServerThread implements Runnable {
		Runnable onReady;

		private void onReady(Runnable runme) {
			onReady = runme;
		}

		public void run() {
			Logger.d("starting serverthread");
			Socket socket = null;
			try {
				serverSocket = new ServerSocket(subscriptionPort);
			} catch (IOException e) {
				e.printStackTrace();
			}

			Logger.d("running");
			if(onReady != null)
				onReady.run();

//			while (!Thread.currentThread().isInterrupted()) {
		  while(true) {
				try {
					if(serverSocket == null)
						return;
					socket = serverSocket.accept();

					Map<String, String> headers = new HashMap<String, String>();
					String line;
					Pattern p = Pattern.compile("^([^:]+): (.+)$");
					Matcher matcher;
					BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					while ((line = reader.readLine()) != null)
					{
						matcher = p.matcher(line);
						if(matcher.find()) {
							headers.put(matcher.group(1), matcher.group(2));
						}
						if(line.equals(""))
						{
							break; // and don't get the next line!
						}
					}
					int contentLength = Integer.parseInt(headers.get("Content-Length"));

					StringBuilder requestContent = new StringBuilder();
					for (int i = 0; i < contentLength; i++)
					{
						requestContent.append((char) reader.read());
					}

					/*
					    <Timeline address="192.168.1.101" audioStreamID="158"
					    containerKey="/library/metadata/14"
					    controllable="playPause,stop,shuffle,repeat,volume,stepBack,stepForward,seekTo,subtitleStream,audioStream"
					    duration="9266976" guid="com.plexapp.agents.imdb://tt0090605?lang=en"
					    key="/library/metadata/14" location="fullScreenVideo"
					    machineIdentifier="xxxxxx" mute="0" playQueueItemID="14"
					    port="32400" protocol="http" ratingKey="14" repeat="0" seekRange="0-9266976" shuffle="0"
					    state="playing" subtitleStreamID="-1" time="4087" type="video" volume="1" />
					 */

					String xml = requestContent.toString();
					MediaContainer mediaContainer = new MediaContainer();

//					Logger.d("xml: %s", xml);
					try {
						mediaContainer = serial.read(MediaContainer.class, xml);
					} catch (Resources.NotFoundException e) {
						e.printStackTrace();
					} catch (Exception e) {
						e.printStackTrace();
					}
					onMessage(headers, mediaContainer);


					sendResponse(socket);

					reader.close();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					try {
						if(socket != null) {
							socket.close();
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
		}
	}

	private void sendResponse(Socket socket) {
		if(socket != null && !socket.isClosed()) {
			try {
				Logger.d("Sending response");
				// Send a response
				String response = "Failure: 200 OK";
				PrintStream output = new PrintStream(socket.getOutputStream());
				output.flush();
				output.println("HTTP/1.1 200 OK");
				output.println("Content-Type: text/plain; charset=UTF-8");
				output.println("Access-Control-Allow-Origin: *");
				output.println("Access-Control-Max-Age: 1209600");
				output.println("");
				output.println(response);

				output.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	private void onMessage(Map<String, String> headers, MediaContainer mc) {
		for(Class subscriber : mSubscribers) {
			Intent messageIntent = new Intent(ACTION_MESSAGE);
			messageIntent.putExtra(EXTRA_CLASS, subscriber);
			messageIntent.putParcelableArrayListExtra(EXTRA_TIMELINES, (ArrayList<Timeline>)mc.timelines);
			LocalBroadcastManager.getInstance(PlexSubscriptionService.this).sendBroadcast(messageIntent);
		}

	}
}
