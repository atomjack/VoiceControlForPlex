package com.atomjack.vcfp.activities;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.atomjack.vcfp.BuildConfig;
import com.atomjack.vcfp.LocalScan;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexSubscriptionReceiver;
import com.atomjack.vcfp.PlexSubscriptionService;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.ScanHandler;
import com.atomjack.vcfp.ServerFindHandler;
import com.atomjack.vcfp.UriDeserializer;
import com.atomjack.vcfp.UriSerializer;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.handlers.BitmapHandler;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexDevice;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.Timeline;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.bugsense.trace.BugSenseHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class VCFPActivity extends ActionBarActivity {
	protected PlexMedia nowPlayingMedia;
	protected boolean subscribed = false;
	protected boolean subscribing = false;
	protected PlexClient subscribedClient;

	protected BroadcastReceiver subscriptionReceiver = new PlexSubscriptionReceiver();

	public final static String BUGSENSE_APIKEY = "879458d0";
	protected Menu menu;

	protected LocalScan localScan;

	int mNotificationId = 001;
	NotificationManager mNotifyMgr;

	protected Gson gsonRead = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriDeserializer())
					.create();

	protected Gson gsonWrite = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriSerializer())
					.create();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(BuildConfig.USE_BUGSENSE)
			BugSenseHandler.initAndStartSession(getApplicationContext(), BUGSENSE_APIKEY);

		mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		if(intent.getAction().equals(PlexSubscriptionService.ACTION_SUBSCRIBED)) {
			MenuItem castIcon = menu.findItem(R.id.action_cast);
			castIcon.setIcon(R.drawable.mr_ic_media_route_on_holo_dark);
			subscribed = true;
			subscribing = false;
		} else if(intent.getAction().equals(PlexSubscriptionService.ACTION_UNSUBSCRIBED)) {
			MenuItem castIcon = menu.findItem(R.id.action_cast);
			castIcon.setIcon(R.drawable.mr_ic_media_route_holo_dark);
			subscribed = false;
			nowPlayingMedia = null;
			mNotifyMgr.cancel(mNotificationId);
		} else if(intent.getAction().equals(PlexSubscriptionService.ACTION_MESSAGE)) {
			ArrayList<Timeline> timelines = intent.getParcelableArrayListExtra(PlexSubscriptionService.EXTRA_TIMELINES);
			if(timelines != null) {
				for (Timeline t : timelines) {
					if (t.type.equals("video")) {
						if(!t.state.equals("stopped")) {
							Logger.d("found non stopped media: %s", t.key);
							if(nowPlayingMedia == null) {
								Logger.d("nowplayingmedia is null");
								// Get this media's info
								PlexServer server = null;
								Logger.d("HAve %d servers", VoiceControlForPlexApplication.servers.size());
								for(PlexServer s : VoiceControlForPlexApplication.servers.values()) {
									if(s.machineIdentifier.equals(t.machineIdentifier)) {
										server = s;
										break;
									}
								}
								if(server == null) {
									// TODO: Scan servers for this server, then get playing media
									Logger.d("server is null");
								} else {
									getPlayingMedia(server, t);
								}
							} else {
								// TODO: now playing media has been defined.
							}
						}
					}
				}
			}
		}
		super.onNewIntent(intent);
	}

	private void getPlayingMedia(final PlexServer server, final Timeline timeline) {
		final Handler handler = new Handler();
		server.findServerConnection(new ServerFindHandler() {
			@Override
			public void onSuccess() {
				PlexHttpClient.get(server, timeline.key, new PlexHttpMediaContainerHandler() {
					@Override
					public void onSuccess(MediaContainer mediaContainer) {
						nowPlayingMedia = mediaContainer.videos.get(0);
						nowPlayingMedia.server = server;
						Logger.d("We're watching %s", nowPlayingMedia.title);
						nowPlayingMedia.getThumb(64, 64, new BitmapHandler() {
							@Override
							public void onSuccess(final Bitmap bitmap) {
								Logger.d("got bitmap");
								handler.post(new Runnable() {
									@Override
									public void run() {
										NotificationCompat.Builder mBuilder =
											new NotificationCompat.Builder(getApplicationContext())
												.setLargeIcon(bitmap)
												.setSmallIcon(R.drawable.ic_launcher)
												.setContentTitle(nowPlayingMedia.title)
												.setContentText(String.format("Playing on: %s", subscribedClient.name));
										mNotifyMgr.notify(mNotificationId, mBuilder.build());
									}
								});

							}
						});
					}

					@Override
					public void onFailure(Throwable error) {
						// TODO: Handle failure
					}
				});
			}

			@Override
			public void onFailure(int statusCode) {

			}
		});
	}

	public static Bitmap getBitmapFromURL(String src) {
		try {
			URL url = new URL(src);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoInput(true);
			connection.connect();
			InputStream input = connection.getInputStream();
			Bitmap myBitmap = BitmapFactory.decodeStream(input);
			return myBitmap;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu _menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, _menu);
		menu = _menu;

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_cast:
				if(!subscribed && !subscribing) {
					subscribing = true;
					localScan.showPlexClients(VoiceControlForPlexApplication.clients, false, onClientConnect);
				} else if(!subscribing) {
					AlertDialog.Builder subscribeDialog = new AlertDialog.Builder(this)
						.setTitle(R.string.connected_to)
						.setMessage(subscribedClient.name)
						.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								dialogInterface.dismiss();
							}
						})
						.setNegativeButton(R.string.disconnect, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								Intent unsubIntent = new Intent(getApplicationContext(), PlexSubscriptionService.class);
								unsubIntent.setAction(PlexSubscriptionService.ACTION_UNSUBSCRIBE);
								unsubIntent.putExtra(PlexSubscriptionService.EXTRA_CLIENT, gsonRead.toJson(subscribedClient));
								unsubIntent.putExtra(PlexSubscriptionService.EXTRA_CLASS, MainActivity.class);
								startService(unsubIntent);
								dialogInterface.dismiss();
							}
						});
					subscribeDialog.show();
				}
				break;
			default:
				break;
		}

		return true;
	}

	protected ScanHandler onClientConnect = new ScanHandler() {
		@Override
		public void onDeviceSelected(PlexDevice device, boolean resume) {
			PlexClient clientSelected = (PlexClient)device;

			// Start animating the action bar icon
			final MenuItem castIcon = menu.findItem(R.id.action_cast);
			castIcon.setIcon(R.drawable.mr_ic_media_route_connecting_holo_dark);
			AnimationDrawable ad = (AnimationDrawable) castIcon.getIcon();
			ad.start();

			subscribedClient = clientSelected;
			Intent subscribeIntent = new Intent(getApplicationContext(), PlexSubscriptionService.class);
			subscribeIntent.setAction(PlexSubscriptionService.ACTION_SUBSCRIBE);
			subscribeIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT, gsonRead.toJson(clientSelected));
			subscribeIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLASS, MainActivity.class);
			startService(subscribeIntent);


		}
	};

	@Override
	protected void onDestroy() {
		if(subscriptionReceiver != null) {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(subscriptionReceiver);
		}
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		if(subscriptionReceiver != null) {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(subscriptionReceiver);
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		if(subscriptionReceiver != null) {
			IntentFilter filters =  new IntentFilter();
			filters.addAction(PlexSubscriptionService.ACTION_SUBSCRIBED);
			filters.addAction(PlexSubscriptionService.ACTION_UNSUBSCRIBED);
			filters.addAction(PlexSubscriptionService.ACTION_MESSAGE);
			LocalBroadcastManager.getInstance(this).registerReceiver(subscriptionReceiver,
							filters);
		}
		super.onResume();
	}
}
