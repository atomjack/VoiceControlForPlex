package com.atomjack.vcfp.activities;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.atomjack.vcfp.BuildConfig;
import com.atomjack.vcfp.LocalScan;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlayerState;
import com.atomjack.vcfp.PlexSubscription;
import com.atomjack.vcfp.VCFPSingleton;
import com.atomjack.vcfp.services.PlexControlService;
import com.atomjack.vcfp.Preferences;
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
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public abstract class VCFPActivity extends ActionBarActivity implements PlexSubscription.Listener {
	protected PlexMedia nowPlayingMedia;
	protected boolean subscribed = false;
	protected boolean subscribing = false;
	protected PlexClient subscribedClient;

  protected VoiceControlForPlexApplication app;

	public final static String BUGSENSE_APIKEY = "879458d0";
	protected Menu menu;

//  protected PlexSubscription plexSubscription;

	protected LocalScan localScan;

  protected PlexSubscription plexSubscription;

	protected PlayerState mCurrentState = PlayerState.STOPPED;
  protected int position = -1;

	int mNotificationId = 0;
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

    app = (VoiceControlForPlexApplication)getApplication();
    plexSubscription = VCFPSingleton.getInstance().getPlexSubscription();
    if(plexSubscription.isSubscribed())
      subscribedClient = plexSubscription.mClient;


		if(BuildConfig.USE_BUGSENSE)
			BugSenseHandler.initAndStartSession(getApplicationContext(), BUGSENSE_APIKEY);

		mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		Preferences.setContext(getApplicationContext());
		Type serverType = new TypeToken<ConcurrentHashMap<String, PlexServer>>(){}.getType();
		VoiceControlForPlexApplication.servers = gsonRead.fromJson(Preferences.get(Preferences.SAVED_SERVERS, ""), serverType);

    // Send an intent to check whether or not we are subscribed
//    plexSubscription = getIntent().getParcelableExtra(VoiceControlForPlexApplication.Intent.EXTRA_SUBSCRIPTION);
//    if(plexSubscription == null)
//      plexSubscription = new PlexSubscription();
	}

	@Override
	protected void onNewIntent(Intent intent) {
    /*
		if(intent.getAction().equals(PlexSubscription.ACTION_SUBSCRIBED)) {
			MenuItem castIcon = menu.findItem(R.id.action_cast);
			castIcon.setIcon(R.drawable.mr_ic_media_route_on_holo_dark);
			subscribed = true;
			subscribing = false;
		} else if(intent.getAction().equals(PlexSubscription.ACTION_UNSUBSCRIBED)) {
			MenuItem castIcon = menu.findItem(R.id.action_cast);
			castIcon.setIcon(R.drawable.mr_ic_media_route_holo_dark);
			subscribed = false;
			nowPlayingMedia = null;
			mNotifyMgr.cancel(mNotificationId);
		} else if(intent.getAction().equals(PlexSubscription.ACTION_MESSAGE)) {
//			Logger.d("VCFP got message");
			ArrayList<Timeline> timelines = intent.getParcelableArrayListExtra(PlexSubscription.EXTRA_TIMELINES);
			if(timelines != null) {
				for (Timeline t : timelines) {
          // TODO: Handle music too
					if (t.type.equals("video")) {
						if(!t.state.equals("stopped") && nowPlayingMedia == null) {
							// Get this media's info
							PlexServer server = null;
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
						}

						if(nowPlayingMedia != null) {
							if(t.key != null && t.key.equals(nowPlayingMedia.key)) {
								// Found an update for the currently playing media
								PlayerState newState = PlayerState.getState(t.state);
								if(newState != mCurrentState) {
									if(newState == PlayerState.PLAYING) {
										Logger.d("client is now playing");
										if(mCurrentState == PlayerState.STOPPED) {
											// We're already subscribed and the client has started playing
                      // TODO: Continue this
										}
									} else if(newState == PlayerState.PAUSED) {
										Logger.d("client is now paused");
									} else if(newState == PlayerState.STOPPED) {
										Logger.d("client is now stopped");
										mNotifyMgr.cancel(mNotificationId);
										nowPlayingMedia = null;
									}
									mCurrentState = newState;
//                  if(mCurrentState != PlayerState.STOPPED)
//                    setNotification();
								}
							}
              position = t.time;
						}
            onSubscriptionMessage(t);
					}
				}
			}
		}
		*/
		super.onNewIntent(intent);
	}

  protected void onSubscriptionMessage(Timeline timeline) {
  }

  private void getPlayingMedia(final PlexServer server, final Timeline timeline) {
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

//                NotificationCompat.Action rewindAction =


                setNotification();


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

  private void setNotification() {
    Logger.d("Setting notification");
    if(subscribedClient != null) {
      Intent rewindIntent = new Intent(this, PlexControlService.class);
      rewindIntent.setAction(PlexControlService.ACTION_REWIND);
      rewindIntent.putExtra(PlexControlService.CLIENT, subscribedClient);
      PendingIntent piRewind = PendingIntent.getService(this, 0, rewindIntent, 0);

      Intent playPauseIntent = new Intent(this, PlexControlService.class);
      int playPauseButton;
      String playPauseAction;
      if (mCurrentState == PlayerState.PLAYING) {
        playPauseButton = R.drawable.button_pause;
        playPauseAction = PlexControlService.ACTION_PAUSE;
      } else {
        playPauseButton = R.drawable.button_play;
        playPauseAction = PlexControlService.ACTION_PLAY;
      }
      playPauseIntent.setAction(playPauseAction);
      playPauseIntent.putExtra(PlexControlService.CLIENT, subscribedClient);
      PendingIntent piPlayPause = PendingIntent.getService(this, 0, playPauseIntent, 0);

      Intent nowPlayingIntent = new Intent(this, NowPlayingActivity.class);
      nowPlayingIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
              Intent.FLAG_ACTIVITY_CLEAR_TASK);
      nowPlayingIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_MEDIA, nowPlayingMedia);
      nowPlayingIntent.putExtra("client", subscribedClient);
      PendingIntent piNowPlaying = PendingIntent.getActivity(this, 0, nowPlayingIntent, PendingIntent.FLAG_UPDATE_CURRENT);

      try {
        NotificationCompat.Builder mBuilder =
          new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.ic_launcher)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(nowPlayingMedia.title)
            .setContentText(String.format("Playing on: %s", subscribedClient.name))
            .addAction(R.drawable.button_rewind, "rewind", piRewind)
            .addAction(playPauseButton, "play", piPlayPause)
            .setContentIntent(piNowPlaying)
            .setDefaults(Notification.DEFAULT_ALL);
        Notification n = mBuilder.build();
        // Disable notification sound
        n.defaults = 0;
        mNotifyMgr.notify(mNotificationId, n);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_cast:
				if(!subscribed && !subscribing) {
					subscribing = true;
					localScan.showPlexClients(VoiceControlForPlexApplication.clients, false, onClientChosen);
				} else if(!subscribing) {
					AlertDialog.Builder subscribeDialog = new AlertDialog.Builder(this)
						.setTitle(R.string.connected_to)
						.setMessage(subscribedClient.name)
						.setNegativeButton(R.string.disconnect, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
                plexSubscription.unsubscribe();
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

	protected ScanHandler onClientChosen = new ScanHandler() {
		@Override
		public void onDeviceSelected(PlexDevice device, boolean resume) {
			PlexClient clientSelected = (PlexClient)device;

			// Start animating the action bar icon
			final MenuItem castIcon = menu.findItem(R.id.action_cast);
			castIcon.setIcon(R.drawable.mr_ic_media_route_connecting_holo_dark);
			AnimationDrawable ad = (AnimationDrawable) castIcon.getIcon();
			ad.start();

			subscribedClient = clientSelected;
      plexSubscription.startSubscription(subscribedClient);
//			Intent subscribeIntent = new Intent(getApplicationContext(), PlexSubscriptionService.class);
//			subscribeIntent.setAction(PlexSubscriptionService.ACTION_SUBSCRIBE);
//			subscribeIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT, gsonRead.toJson(clientSelected));
//			subscribeIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLASS, MainActivity.class);
//			startService(subscribeIntent);
		}
	};

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
    plexSubscription.setListener(null);
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    Logger.d("Saving instance state");
//    outState.putBoolean(VoiceControlForPlexApplication.Intent.SUBSCRIBED, subscribed);
//    outState.putParcelable(VoiceControlForPlexApplication.Intent.EXTRA_MEDIA, nowPlayingMedia);
//    outState.putParcelable("client", client);
  }

  @Override
  public void onSubscribed() {
    MenuItem castIcon = menu.findItem(R.id.action_cast);
    castIcon.setIcon(R.drawable.mr_ic_media_route_on_holo_dark);
    subscribed = true;
    subscribing = false;
  }

  @Override
  public void onMessageReceived(MediaContainer mc) {
    List<Timeline> timelines = mc.timelines;
    if(timelines != null) {
      for (Timeline t : timelines) {
        // TODO: Handle music too
        if (t.type.equals("video")) {
          if(!t.state.equals("stopped") && nowPlayingMedia == null) {
            // Get this media's info
            PlexServer server = null;
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
          }

          if(nowPlayingMedia != null) {
            if(t.key != null && t.key.equals(nowPlayingMedia.key)) {
              // Found an update for the currently playing media
              PlayerState newState = PlayerState.getState(t.state);
              if(newState != mCurrentState) {
                if(newState == PlayerState.PLAYING) {
                  Logger.d("client is now playing");
                  if(mCurrentState == PlayerState.STOPPED) {
                    // We're already subscribed and the client has started playing
                    // TODO: Continue this
                  }
                } else if(newState == PlayerState.PAUSED) {
                  Logger.d("client is now paused");
                } else if(newState == PlayerState.STOPPED) {
                  Logger.d("client is now stopped");
                  mNotifyMgr.cancel(mNotificationId);
                  nowPlayingMedia = null;
                }
                mCurrentState = newState;
                if(mCurrentState != PlayerState.STOPPED)
                  setNotification();
              }
            }
            position = t.time;
          }
          if(plexSubscription.getListener() != null)
            onSubscriptionMessage(t);
        }
      }
    }
  }

  @Override
  public void onUnsubscribed() {
    Logger.d("VCFPActivity onUnsubscribed");
    MenuItem castIcon = menu.findItem(R.id.action_cast);
    castIcon.setIcon(R.drawable.mr_ic_media_route_holo_dark);
    subscribed = false;
    nowPlayingMedia = null;
    mNotifyMgr.cancel(mNotificationId);
  }
}
