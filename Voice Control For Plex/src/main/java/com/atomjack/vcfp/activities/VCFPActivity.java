package com.atomjack.vcfp.activities;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.atomjack.vcfp.BuildConfig;
import com.atomjack.vcfp.CastPlayerManager;
import com.atomjack.vcfp.Feedback;
import com.atomjack.vcfp.LocalScan;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlayerState;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.PlexSubscription;
import com.atomjack.vcfp.VCFPSingleton;
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
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.model.Timeline;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.bugsense.trace.BugSenseHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.loopj.android.http.BinaryHttpResponseHandler;

import org.apache.http.Header;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import cz.fhucho.android.util.SimpleDiskCache;

public abstract class VCFPActivity extends ActionBarActivity implements PlexSubscription.Listener, CastPlayerManager.Listener {
	protected PlexMedia nowPlayingMedia;
	protected boolean subscribed = false;
	protected boolean subscribing = false;
	protected PlexClient mClient;

  protected VoiceControlForPlexApplication app;

	public final static String BUGSENSE_APIKEY = "879458d0";
	protected Menu menu;

  protected Handler mHandler;

	protected LocalScan localScan;

  protected PlexSubscription plexSubscription;
  protected CastPlayerManager castPlayerManager;

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

  protected Feedback feedback;

  SimpleDiskCache mSimpleDiskCache;


  @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

    Preferences.setContext(getApplicationContext());

    mSimpleDiskCache = VCFPSingleton.getInstance().getSimpleDiskCache(this);


    app = (VoiceControlForPlexApplication)getApplication();
    feedback = new Feedback(this);

    plexSubscription = VCFPSingleton.getInstance().getPlexSubscription();
    if(plexSubscription.isSubscribed()) {
      Logger.d("VCFPActivity setting client to %s", plexSubscription.mClient);
      mClient = plexSubscription.mClient;
    } else {
      Logger.d("Not subscribed: %s", plexSubscription.mClient);
    }

    castPlayerManager = VCFPSingleton.getInstance().getCastPlayerManager(this);
    if(castPlayerManager.isSubscribed())
      mClient = castPlayerManager.mClient;

		if(BuildConfig.USE_BUGSENSE)
			BugSenseHandler.initAndStartSession(getApplicationContext(), BUGSENSE_APIKEY);

		mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    mHandler = new Handler();


		Type serverType = new TypeToken<ConcurrentHashMap<String, PlexServer>>(){}.getType();
		VoiceControlForPlexApplication.servers = gsonRead.fromJson(Preferences.get(Preferences.SAVED_SERVERS, ""), serverType);

    // Send an intent to check whether or not we are subscribed
//    plexSubscription = getIntent().getParcelableExtra(VoiceControlForPlexApplication.Intent.EXTRA_SUBSCRIPTION);
//    if(plexSubscription == null)
//      plexSubscription = new PlexSubscription();
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
            if(timeline.type.equals("video"))
						  nowPlayingMedia = mediaContainer.videos.get(0);
            else if(timeline.type.equals("music"))
              nowPlayingMedia = mediaContainer.tracks.get(0);
            else {
              // TODO: Handle failure
            }
						nowPlayingMedia.server = server;
						Logger.d("We're watching %s", nowPlayingMedia.title);
						nowPlayingMedia.getThumb(64, 64, new BitmapHandler() {
							@Override
							public void onSuccess(final Bitmap bitmap) {
								Logger.d("got bitmap");

//                NotificationCompat.Action rewindAction =


                //setNotification();
                Logger.d("getPlayingMedia setting notification with %s", nowPlayingMedia);
                VoiceControlForPlexApplication.setNotification(getApplicationContext(), mClient, mCurrentState, nowPlayingMedia);

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

  private boolean isSubscribed() {
    return plexSubscription.isSubscribed() || castPlayerManager.isSubscribed();
  }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_cast:
        Logger.d("subscribed: %s", subscribed);
        Logger.d("subscribing: %s", subscribing);
				if(!isSubscribed() && !subscribing) {
					subscribing = true;
					localScan.showPlexClients(VoiceControlForPlexApplication.clients, false, onClientChosen);
				} else if(!subscribing) {
					AlertDialog.Builder subscribeDialog = new AlertDialog.Builder(this)
						.setTitle(R.string.connected_to)
						.setMessage(mClient.name)
						.setNegativeButton(R.string.disconnect, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                if(mClient.isCastClient)
                  castPlayerManager.unsubscribe();
                else
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
      subscribing = false;
      if(device != null) {
        PlexClient clientSelected = (PlexClient) device;

        // Start animating the action bar icon
        final MenuItem castIcon = menu.findItem(R.id.action_cast);
        castIcon.setIcon(R.drawable.mr_ic_media_route_connecting_holo_dark);
        AnimationDrawable ad = (AnimationDrawable) castIcon.getIcon();
        ad.start();

        mClient = clientSelected;
        if (mClient.isCastClient) {

          castPlayerManager.subscribe(mClient);
        } else
          plexSubscription.startSubscription(mClient);
      }
		}
	};

  protected void castSubscribe() {}

	@Override
	protected void onDestroy() {
		super.onDestroy();
    plexSubscription.removeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
    if(!plexSubscription.isSubscribed() && !castPlayerManager.isSubscribed())
      subscribed = false;
    else
      subscribed = true;
	}

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    Logger.d("Saving instance state");
//    outState.putBoolean(VoiceControlForPlexApplication.Intent.SUBSCRIBED, subscribed);
//    outState.putParcelable(VoiceControlForPlexApplication.Intent.EXTRA_MEDIA, nowPlayingMedia);
//    outState.putParcelable("mClient", mClient);
  }

  @Override
  public void onSubscribed(PlexClient _client) {
    Logger.d("VCFPActivity: onSubscribed: %s", _client);
    mClient = _client;
    try {
      setCastIconActive();
      subscribed = true;
      subscribing = false;
    } catch (Exception e) {
      e.printStackTrace();
    }
    feedback.m(String.format(getString(R.string.connected_to2), mClient.name));
  }

  protected void setCastIconActive() {
    MenuItem castIcon = menu.findItem(R.id.action_cast);
    castIcon.setIcon(R.drawable.mr_ic_media_route_on_holo_dark);
  }

  @Override
  public void onCastConnected(PlexClient _client) {
    onSubscribed(_client);
  }

  @Override
  public void onCastDisconnected() {
    onUnsubscribed();
  }

  @Override
  public void onCastPlayerStateChanged(int status) {

  }

  @Override
  public void onCastPlayerTimeUpdate(int seconds) {

  }

  @Override
  public void onMessageReceived(MediaContainer mc) {
    List<Timeline> timelines = mc.timelines;
    if(timelines != null) {
      for (Timeline t : timelines) {
        // TODO: Handle music too
        if (t.key != null) {
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
              nowPlayingMedia.viewOffset = Integer.toString(t.time);
              if(newState != mCurrentState) {
                if(newState == PlayerState.PLAYING) {
                  Logger.d("mClient is now playing");
                  if(mCurrentState == PlayerState.STOPPED) {
                    // We're already subscribed and the mClient has started playing
                    // TODO: Continue this
                  }
                } else if(newState == PlayerState.PAUSED) {
                  Logger.d("mClient is now paused");
                } else if(newState == PlayerState.STOPPED) {
                  Logger.d("mClient is now stopped");
                  mNotifyMgr.cancel(mNotificationId);
                  nowPlayingMedia = null;
                }
                mCurrentState = newState;
                if(mCurrentState != PlayerState.STOPPED) {
                  Logger.d("onMessageReceived setting notification with %s", mCurrentState);
                  VoiceControlForPlexApplication.setNotification(getApplicationContext(), mClient, mCurrentState, nowPlayingMedia);
                }
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
    feedback.m(R.string.disconnected);
  }

  private void getThumb(final String thumb, final PlexMedia media) {
    String url = String.format("http://%s:%s%s", media.server.activeConnection.address, media.server.activeConnection.port, thumb);
    if(media.server.accessToken != null)
      url += String.format("?%s=%s", PlexHeaders.XPlexToken, media.server.accessToken);

    PlexHttpClient.getClient().get(url, new BinaryHttpResponseHandler() {
      @Override
      public void onSuccess(int i, Header[] headers, byte[] imageData) {
        InputStream is = new ByteArrayInputStream(imageData);

        try {
          is.reset();
        } catch (IOException e) {
          e.printStackTrace();
        }

        // Save the downloaded image into the disk cache so we don't have to download it again
        try {
          mSimpleDiskCache.put(String.format("%s/%s", media.server.machineIdentifier, thumb), is);
        } catch (Exception ex) {
          ex.printStackTrace();
        }
        setThumb(is);
      }

      @Override
      public void onFailure(int i, Header[] headers, byte[] bytes, Throwable throwable) {

      }
    });
  }

  @SuppressWarnings("deprecation")
  private void setThumb(InputStream is) {
    Logger.d("Setting thumb: %s", is);
    final RelativeLayout layout = (RelativeLayout)findViewById(R.id.background);

    try {
      is.reset();
    } catch (IOException e) {
    }

    Drawable d = Drawable.createFromStream(is, "thumb");
    Logger.d("d: %s", d);
    d.setAlpha(80);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
      layout.setBackground(d);
    else
      layout.setBackgroundDrawable(d);
  }

  public void setThumb(PlexMedia media, int orientation) {
    if(!media.thumb.equals("")) {
      String thumb = media.thumb;
      if(media instanceof PlexVideo) {
        PlexVideo video = (PlexVideo)media;
        thumb = video.type.equals("movie") ? video.thumb : video.grandparentThumb;
        Logger.d("orientation: %s, type: %s", orientation, video.type);
        if(orientation == Configuration.ORIENTATION_LANDSCAPE) {
          if(video.type.equals("movie"))
            thumb = video.art;
          else if(video.type.equals("episode")) {
            thumb = video.thumb;
          }
        }
      }


      Logger.d("thumb: %s", thumb);

      SimpleDiskCache.InputStreamEntry thumbEntry = null;
      try {
        thumbEntry = mSimpleDiskCache.getInputStream(String.format("%s/%s", media.server.machineIdentifier, thumb));
      } catch (Exception ex) {}
      if(thumbEntry != null) {
        Logger.d("Using cached thumb");
        setThumb(thumbEntry.getInputStream());
      } else {
        Logger.d("Downloading thumb");
        getThumb(thumb, media);
      }
    }
  }
}
