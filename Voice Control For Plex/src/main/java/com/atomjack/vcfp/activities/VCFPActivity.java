package com.atomjack.vcfp.activities;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.android.vending.billing.IabHelper;
import com.android.vending.billing.IabResult;
import com.android.vending.billing.Purchase;
import com.atomjack.vcfp.BuildConfig;
import com.atomjack.vcfp.CastPlayerManager;
import com.atomjack.vcfp.Feedback;
import com.atomjack.vcfp.LocalScan;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.NetworkMonitor;
import com.atomjack.vcfp.PlayerState;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.PlexSubscription;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.ScanHandler;
import com.atomjack.vcfp.ServerFindHandler;
import com.atomjack.vcfp.UriDeserializer;
import com.atomjack.vcfp.UriSerializer;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexDevice;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.model.Timeline;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.bugsense.trace.BugSenseHandler;
import com.google.android.gms.cast.MediaStatus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.loopj.android.http.BinaryHttpResponseHandler;

import org.apache.http.Header;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import cz.fhucho.android.util.SimpleDiskCache;

public abstract class VCFPActivity extends ActionBarActivity implements PlexSubscription.PlexListener, CastPlayerManager.CastListener, VoiceControlForPlexApplication.NetworkChangeListener {
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

  protected boolean continuing = false;

	int mNotificationId = 0;
	NotificationManager mNotifyMgr;

  protected NetworkMonitor networkMonitor;

	protected Gson gsonRead = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriDeserializer())
					.create();

	protected Gson gsonWrite = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriSerializer())
					.create();

  protected Feedback feedback;

  SimpleDiskCache mSimpleDiskCache;

  protected PlexClient postChromecastPurchaseClient = null;
  protected Runnable postChromecastPurchaseAction = null;

  public enum NetworkState {
    DISCONNECTED,
    WIFI,
    MOBILE;

    public static NetworkState getCurrentNetworkState(Context context) {
      NetworkState currentNetworkState;
      ConnectivityManager cm =
              (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

      NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
      if(activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE)
        currentNetworkState = NetworkState.MOBILE;
      else if(activeNetwork.getType() == ConnectivityManager.TYPE_WIFI)
        currentNetworkState = NetworkState.WIFI;
      else
        currentNetworkState = NetworkState.DISCONNECTED;
      return currentNetworkState;
    }
  };
  protected NetworkState currentNetworkState;

  @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

    Preferences.setContext(getApplicationContext());

    mSimpleDiskCache = VoiceControlForPlexApplication.getInstance().mSimpleDiskCache;

    app = (VoiceControlForPlexApplication)getApplication();
    feedback = new Feedback(this);

    plexSubscription = VoiceControlForPlexApplication.getInstance().plexSubscription;
    if(plexSubscription.isSubscribed()) {
      Logger.d("VCFPActivity setting client to %s", plexSubscription.mClient);
      mClient = plexSubscription.mClient;
    } else {
      Logger.d("Not subscribed: %s", plexSubscription.mClient);
    }

    networkMonitor = new NetworkMonitor(this);
    VoiceControlForPlexApplication.getInstance().setNetworkChangeListener(this);

    currentNetworkState = NetworkState.getCurrentNetworkState(this);



    castPlayerManager = VoiceControlForPlexApplication.getInstance().castPlayerManager;
    castPlayerManager.setContext(this);
    if(castPlayerManager.isSubscribed())
      mClient = castPlayerManager.mClient;

		if(BuildConfig.USE_BUGSENSE)
			BugSenseHandler.initAndStartSession(getApplicationContext(), BUGSENSE_APIKEY);

		mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    mHandler = new Handler();
	}

  protected void onSubscriptionMessage(Timeline timeline) {
  }

  private void getPlayingMedia(final PlexServer server, final Timeline timeline) {
    Logger.d("[VCFPActivity] getPlayingMedia: %s", timeline.key);
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
              Logger.d("Failed to get media with type %s", timeline.type);
            }
						nowPlayingMedia.server = server;

            VoiceControlForPlexApplication.getInstance().setNotification(mClient, mCurrentState, nowPlayingMedia);
            if(timeline.continuing != null && timeline.continuing.equals("1"))
              continuing = true;
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
        Logger.d("[VCFPActivity] subscribed: %s", isSubscribed());
        Logger.d("[VCFPActivity] subscribing: %s", subscribing);
				if(!isSubscribed() && !subscribing) {
					subscribing = true;
          if(VoiceControlForPlexApplication.clients.size() == 0 && !VoiceControlForPlexApplication.hasDoneClientScan) {
            localScan.searchForPlexClients(true);
          } else
  					localScan.showPlexClients(false, onClientChosen);
				} else if(!subscribing) {
					AlertDialog.Builder subscribeDialog = new AlertDialog.Builder(this)
						.setTitle(mClient.name)
            .setIcon(R.drawable.mr_ic_media_route_on_holo_dark)
						.setNegativeButton(R.string.disconnect, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                if (mClient.isCastClient)
                  castPlayerManager.unsubscribe();
                else
                  plexSubscription.unsubscribe();
                dialogInterface.dismiss();
              }
            });
          if(mClient.isCastClient) {
            View subscribeVolume = LayoutInflater.from(this).inflate(R.layout.connected_popup, null);
            subscribeDialog.setView(subscribeVolume);
          }
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
        Logger.d("[VCFPActivity] onClientChosen: %s", device.name);
        PlexClient clientSelected = (PlexClient) device;

        // Start animating the action bar icon
        final MenuItem castIcon = menu.findItem(R.id.action_cast);
        castIcon.setIcon(R.drawable.mr_ic_media_route_connecting_holo_dark);
        AnimationDrawable ad = (AnimationDrawable) castIcon.getIcon();
        ad.start();


        if (clientSelected.isCastClient) {
          if(VoiceControlForPlexApplication.getInstance().hasChromecast()) {
            mClient = clientSelected;
            Logger.d("[VCPActivity] subscribing");
            castPlayerManager.subscribe(mClient);
          } else {
            showChromecastPurchase(clientSelected, new Runnable() {
              @Override
              public void run() {
                castPlayerManager.subscribe(postChromecastPurchaseClient);
              }
            });
          }
        } else {
          plexSubscription.startSubscription(clientSelected);
        }
      }
		}
	};

  protected void showChromecastPurchase(PlexClient client, Runnable onSuccess) {
    postChromecastPurchaseClient = client;
    postChromecastPurchaseAction = onSuccess;
    new AlertDialog.Builder(VCFPActivity.this)
            .setMessage(String.format(getString(R.string.must_purchase_chromecast), VoiceControlForPlexApplication.getChromecastPrice()))
            .setCancelable(false)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
                VoiceControlForPlexApplication.getInstance().getIabHelper().launchPurchaseFlow(VCFPActivity.this, VoiceControlForPlexApplication.SKU_CHROMECAST, 10001, mPurchaseFinishedListener, VoiceControlForPlexApplication.SKU_TEST_PURCHASED == VoiceControlForPlexApplication.SKU_CHROMECAST ? VoiceControlForPlexApplication.getInstance().getEmailHash() : "");
              }
            })
            .setNeutralButton(R.string.no_thanks, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                setCastIconInactive();
              }
            }).create().show();
  }

  IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener
          = new IabHelper.OnIabPurchaseFinishedListener() {
    public void onIabPurchaseFinished(IabResult result, Purchase purchase)
    {
      if (result.isFailure()) {
        Logger.d("Error purchasing: " + result);
        if(result.getResponse() != -1005) {
          feedback.e(result.getMessage());
        }
        // Only reset the cast icon if we aren't subscribed (if we are, the only way to get here is through main client selection)
        if(!isSubscribed())
          setCastIconInactive();
        return;
      }
      else if (purchase.getSku().equals(VoiceControlForPlexApplication.SKU_CHROMECAST)) {
        Logger.d("Purchased chromecast!");
        VoiceControlForPlexApplication.getInstance().setHasChromecast(true);
        if(postChromecastPurchaseAction != null) {
          postChromecastPurchaseAction.run();
        }
      }
    }
  };

	@Override
	protected void onDestroy() {
		super.onDestroy();
    networkMonitor.unregister();
    plexSubscription.removeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
    networkMonitor.unregister();
	}

	@Override
	protected void onResume() {
		super.onResume();
    Logger.d("[VCFPActivity] onResume");
    if(!plexSubscription.isSubscribed() && !castPlayerManager.isSubscribed()) {
      subscribed = false;
      setCastIconInactive();
    } else {
      setCastIconActive();
      subscribed = true;
    }
    networkMonitor.register();
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

  @Override
  public void onSubscribeError(String errorMessage) {
    feedback.e(String.format(getString(R.string.got_error), errorMessage));
    setCastIconInactive();
  }

  protected void setCastIconInactive() {
    Logger.d("[VCFPActivity] setCastIconInactive");
    try {
      MenuItem castIcon = menu.findItem(R.id.action_cast);
      castIcon.setIcon(R.drawable.mr_ic_media_route_holo_dark);
    } catch (Exception e) {}
  }

  protected void setCastIconActive() {
    Logger.d("[VCFPActivity] setCastIconActive");
    try {
      MenuItem castIcon = menu.findItem(R.id.action_cast);
      castIcon.setIcon(R.drawable.mr_ic_media_route_on_holo_dark);
    } catch (Exception e) {}
  }

  @Override
  public void onCastConnected(PlexClient _client) {
    Logger.d("[VCFPActivity] onCastConnected");
    onSubscribed(_client);
    castPlayerManager.getPlaybackState();
  }

  @Override
  public void onCastPlayerState(PlayerState state, PlexMedia media) {
    mCurrentState = state;
    Logger.d("[VCFPActivity] mCurrentState: %s, media: %s", mCurrentState, media);
    if(!mCurrentState.equals(PlayerState.STOPPED) && media != null) {
      nowPlayingMedia = media;
      mClient = castPlayerManager.mClient;
      VoiceControlForPlexApplication.getInstance().setNotification(mClient, mCurrentState, nowPlayingMedia);
    }
  }

  @Override
  public void onCastDisconnected() {
    onUnsubscribed();
  }

  @Override
  public void onCastPlayerStateChanged(PlayerState state) {
    PlayerState oldState = mCurrentState;
    mCurrentState = state;
    Logger.d("[VCFPActivity] onCastPlayerStateChanged: %s (old state: %s)", mCurrentState, oldState);
    if(mCurrentState != oldState) {
      if(mCurrentState == PlayerState.STOPPED) {
        mNotifyMgr.cancel(mNotificationId);
      } else {
        VoiceControlForPlexApplication.getInstance().setNotification(mClient, mCurrentState, nowPlayingMedia);
      }
    }
  }

  @Override
  public void onCastPlayerTimeUpdate(int seconds) {

  }



  @Override
  public void onTimelineReceived(MediaContainer mc) {
    List<Timeline> timelines = mc.timelines;
    if(timelines != null) {
      for (Timeline timeline : timelines) {
        if (timeline.key != null) {

          if((!timeline.state.equals("stopped") && nowPlayingMedia == null) || continuing) {
            // Get this media's info
            PlexServer server = null;
            for(PlexServer s : VoiceControlForPlexApplication.servers.values()) {
              if(s.machineIdentifier.equals(timeline.machineIdentifier)) {
                server = s;
                break;
              }
            }
            if(server == null) {
              // TODO: Scan servers for this server, then get playing media
              Logger.d("server is null");
            } else {
              getPlayingMedia(server, timeline);
            }
          }

          if(nowPlayingMedia != null) {
            if(timeline.key != null && timeline.key.equals(nowPlayingMedia.key)) {
              // Found an update for the currently playing media
              PlayerState oldState = mCurrentState;
              mCurrentState = PlayerState.getState(timeline.state);
              nowPlayingMedia.viewOffset = Integer.toString(timeline.time);
              if(oldState != mCurrentState) {
                if(mCurrentState == PlayerState.PLAYING) {
                  Logger.d("mClient is now playing");
                  if(mCurrentState == PlayerState.STOPPED) {
                    // We're already subscribed and the mClient has started playing
                    // TODO: Continue this
                  }
                } else if(mCurrentState == PlayerState.PAUSED) {
                  Logger.d("mClient is now paused");
                } else if(mCurrentState == PlayerState.STOPPED) {
                  Logger.d("mClient is now stopped");
                  if(!continuing) {
                    mNotifyMgr.cancel(mNotificationId);
                    nowPlayingMedia = null;
                  }
                }
                if(mCurrentState != PlayerState.STOPPED && oldState != mCurrentState) {
                  Logger.d("onTimelineReceived setting notification with %s", mCurrentState);
                  VoiceControlForPlexApplication.getInstance().setNotification(mClient, mCurrentState, nowPlayingMedia);
                }
              }
            }
            position = timeline.time;
          }
          if(plexSubscription.getListener() != null)
            onSubscriptionMessage(timeline);
        }
      }
    }


  }

  @Override
  public void onUnsubscribed() {
    Logger.d("VCFPActivity onUnsubscribed");
    setCastIconInactive();
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
          mSimpleDiskCache.put(media.getCacheKey(thumb), is);
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

  protected int getOrientation() {
    return getResources().getConfiguration().orientation;
  }

  @SuppressWarnings("deprecation")
  private void setThumb(InputStream is) {
    Logger.d("Setting thumb: %s", is);
    View layout;
    View backgroundLayout = findViewById(R.id.background);
    View musicLayout = findViewById(R.id.nowPlayingMusicCover);
    if(nowPlayingMedia instanceof PlexTrack && getOrientation() == Configuration.ORIENTATION_PORTRAIT) {
      backgroundLayout.setBackground(null);
      layout = musicLayout;
    } else {
      if(musicLayout != null)
        musicLayout.setBackground(null);
      layout = backgroundLayout;
    }

    try {
      is.reset();
    } catch (IOException e) {
    }

    Drawable d = Drawable.createFromStream(is, "thumb");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
      layout.setBackground(d);
    else
      layout.setBackgroundDrawable(d);
  }

  public void setThumb() {
    if(nowPlayingMedia.thumb != null && !nowPlayingMedia.thumb.equals("")) {
      String thumb = nowPlayingMedia.thumb;
      Logger.d("setThumb: %s", thumb);
      if(nowPlayingMedia instanceof PlexVideo) {
        PlexVideo video = (PlexVideo)nowPlayingMedia;
        thumb = video.isMovie() || video.isClip() ? video.thumb : video.grandparentThumb;
        Logger.d("orientation: %s, type: %s", getOrientation(), video.type);
        if(video.isClip()) {

        }

        if(getOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
          thumb = video.art;
        }
      } else if(nowPlayingMedia instanceof PlexTrack && getOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
        PlexTrack track = (PlexTrack)nowPlayingMedia;
        thumb = track.art;
      }


      Logger.d("thumb: %s", thumb);

      if(thumb != null) {
        SimpleDiskCache.InputStreamEntry thumbEntry = null;
        try {
          thumbEntry = mSimpleDiskCache.getInputStream(nowPlayingMedia.getCacheKey(thumb));
        } catch (Exception ex) {
        }
        if (thumbEntry != null) {
          Logger.d("Using cached thumb: %s", nowPlayingMedia.getCacheKey(thumb));
          setThumb(thumbEntry.getInputStream());
        } else {
          Logger.d("Downloading thumb");
          getThumb(thumb, nowPlayingMedia);
        }
      } else {
        Logger.d("Couldn't find a background");
      }
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    Logger.d("onActivityResult(" + requestCode + "," + resultCode + ","
            + data);

    // Pass on the activity result to the helper for handling
    if (VoiceControlForPlexApplication.getInstance().getIabHelper() == null || !VoiceControlForPlexApplication.getInstance().getIabHelper().handleActivityResult(requestCode, resultCode, data)) {
      super.onActivityResult(requestCode, resultCode, data);
    } else {
      Logger.d("onActivityResult handled by IABUtil.");
    }
  }

  protected void showErrorDialog(String title, String message) {
    AlertDialog.Builder errorDialog = new AlertDialog.Builder(this);
    if(title != null)
      errorDialog.setTitle(title);
    if(message != null)
      errorDialog.setMessage(message);
    errorDialog.setNegativeButton(R.string.ok, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        dialogInterface.dismiss();
      }
    });
    errorDialog.show();
  }

  @Override
  public void onConnected(int connectionType) {
    Logger.d("Connected with type %d", connectionType);
    // Only show the cast button if the previous state was disconnected.
    if(currentNetworkState == NetworkState.DISCONNECTED) {
      MenuItem item = menu.findItem(R.id.action_cast);
      item.setVisible(true);
    }

    if(connectionType == ConnectivityManager.TYPE_MOBILE)
      currentNetworkState = NetworkState.MOBILE;
    else if(connectionType == ConnectivityManager.TYPE_WIFI)
      currentNetworkState = NetworkState.WIFI;

  }

  @Override
  public void onDisconnected() {
    Logger.d("Disconnected");
    currentNetworkState = NetworkState.DISCONNECTED;

    // We have no network connection, so hide the cast button
    MenuItem item = menu.findItem(R.id.action_cast);
    item.setVisible(false);
  }

  @Override
  public void onCastPlayerPlaylistAdvance(String key) {

  }
}
