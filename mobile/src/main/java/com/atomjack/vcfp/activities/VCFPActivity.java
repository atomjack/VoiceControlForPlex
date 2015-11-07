package com.atomjack.vcfp.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;

import com.android.vending.billing.IabHelper;
import com.android.vending.billing.IabResult;
import com.android.vending.billing.Purchase;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.WearConstants;
import com.atomjack.vcfp.BuildConfig;
import com.atomjack.vcfp.CastPlayerManager;
import com.atomjack.vcfp.Feedback;
import com.atomjack.shared.Logger;
import com.atomjack.vcfp.NetworkMonitor;
import com.atomjack.shared.PlayerState;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.PlexSubscription;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.interfaces.ActiveConnectionHandler;
import com.atomjack.vcfp.interfaces.BitmapHandler;
import com.atomjack.vcfp.interfaces.InputStreamHandler;
import com.atomjack.vcfp.interfaces.ScanHandler;
import com.atomjack.shared.UriDeserializer;
import com.atomjack.shared.UriSerializer;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.adapters.PlexListAdapter;
import com.atomjack.vcfp.model.Capabilities;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexDevice;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.shared.model.Timeline;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.atomjack.vcfp.services.PlexScannerService;
import com.bugsense.trace.BugSenseHandler;
import com.google.android.gms.wearable.DataMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import cz.fhucho.android.util.SimpleDiskCache;

public abstract class VCFPActivity extends AppCompatActivity implements PlexSubscription.PlexListener, CastPlayerManager.CastListener, VoiceControlForPlexApplication.NetworkChangeListener {
	protected PlexMedia nowPlayingMedia;
	protected boolean subscribing = false;
	protected PlexClient mClient;

  protected static final int REQUEST_WRITE_STORAGE = 112;

  protected VoiceControlForPlexApplication app;

	public final static String BUGSENSE_APIKEY = "879458d0";
	protected Menu menu;

  protected Handler mHandler;

  protected PlexSubscription plexSubscription;
  protected CastPlayerManager castPlayerManager;

	protected PlayerState mCurrentState = PlayerState.STOPPED;
  protected int position = -1;

  protected boolean continuing = false;

  protected NetworkMonitor networkMonitor;

	protected Gson gsonRead = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriDeserializer())
					.create();

	protected Gson gsonWrite = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriSerializer())
					.create();

  protected Feedback feedback;

  public boolean isScanning = false;
  private boolean cancelScan = false;
  protected Dialog deviceSelectDialog = null;

  SimpleDiskCache mSimpleDiskCache;

  protected PlexClient postChromecastPurchaseClient = null;
  protected Runnable postChromecastPurchaseAction = null;

  public enum NetworkState {
    DISCONNECTED,
    WIFI,
    MOBILE;

    public static NetworkState getCurrentNetworkState(Context context) {
      NetworkState currentNetworkState = NetworkState.DISCONNECTED;
      ConnectivityManager cm =
              (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

      NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
      if(activeNetwork != null) {
        if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE)
          currentNetworkState = NetworkState.MOBILE;
        else if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI)
          currentNetworkState = NetworkState.WIFI;
      }
      return currentNetworkState;
    }
  };
  protected NetworkState currentNetworkState;

  protected boolean serverScanCanceled = false;
  protected boolean clientScanCanceled = false;

  @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

    mSimpleDiskCache = VoiceControlForPlexApplication.getInstance().mSimpleDiskCache;

    app = (VoiceControlForPlexApplication)getApplication();
    feedback = new Feedback(this);

    plexSubscription = VoiceControlForPlexApplication.getInstance().plexSubscription;
    castPlayerManager = VoiceControlForPlexApplication.getInstance().castPlayerManager;

    if(gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SUBSCRIBED_CLIENT, ""), PlexClient.class) != null) {
      mClient = gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SUBSCRIBED_CLIENT, ""), PlexClient.class);
      if(mClient.isCastClient) {
        if(!castPlayerManager.isSubscribed()) {
          castPlayerManager.subscribe(mClient);
        }
      } else if(!plexSubscription.isSubscribed()) {
        plexSubscription.subscribe(mClient);
      }
    }

    if(!plexSubscription.isSubscribed() && !castPlayerManager.isSubscribed()) {
      Logger.d("Not subscribed: %s", plexSubscription.mClient);
      // In case the notification is still up due to a crash
      VoiceControlForPlexApplication.getInstance().cancelNotification();
    }

    networkMonitor = new NetworkMonitor(this);
    VoiceControlForPlexApplication.getInstance().setNetworkChangeListener(this);

    currentNetworkState = NetworkState.getCurrentNetworkState(this);


    castPlayerManager.setContext(this);

		if(BuildConfig.USE_BUGSENSE)
			BugSenseHandler.initAndStartSession(getApplicationContext(), BUGSENSE_APIKEY);

    mHandler = new Handler();
	}

  protected void onMediaChange() {

  }
  protected void onSubscriptionMessage(Timeline timeline) {
  }

  private void getPlayingMedia(final PlexServer server, final Timeline timeline) {
    Logger.d("[VCFPActivity] getPlayingMedia: %s", timeline.key);
    // TODO: Find out why server can sometimes be null
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

        if(nowPlayingMedia != null) {
          nowPlayingMedia.server = server;

          VoiceControlForPlexApplication.getInstance().setNotification(mClient, mCurrentState, nowPlayingMedia);
          if (timeline.continuing != null && timeline.continuing.equals("1"))
            continuing = true;
          onMediaChange();
          sendWearPlaybackChange();
        }
      }

      @Override
      public void onFailure(Throwable error) {
        // TODO: Handle failure
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
          clientScanCanceled = false;
          if(VoiceControlForPlexApplication.clients.size() == 0 && !VoiceControlForPlexApplication.hasDoneClientScan) {
            searchForPlexClients(true);
          } else {
            showPlexClients(false, onClientChosen);
            // Kick off a client scan in the background
            searchForPlexClients();
          }
				} else if(!subscribing) {
          // For some reason we sometimes lose mClient here, even though we're subscribed. If we do, let's try to get the client from the subscription manager
//          if(mClient == null) {
//            Logger.d("[VCFPActivity] 0Lost subscribed client.");
            if(castPlayerManager.mClient != null)
              mClient = castPlayerManager.mClient;
            else if(plexSubscription.mClient != null)
              mClient = plexSubscription.mClient;
//          }
          if(mClient == null) {
            Logger.d("Lost subscribed client.");
            setCastIconInactive();
          } else {
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
            if (mClient.isCastClient) {
              View subscribeVolume = LayoutInflater.from(this).inflate(R.layout.connected_popup, null);
              subscribeDialog.setView(subscribeVolume);
            }
            subscribeDialog.show();
          }
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
      Logger.d("[VCFPActivity] onClientChosen onDeviceSelected");

      // Set this to true so that if a client is selected immediately upon the listview being shown, but before
      // the listview is refreshed, it doesn't get shown again. It will be reset to false before the listview is shown again.
      clientScanCanceled = true;

      subscribing = true;
      if(device != null) {
        Logger.d("[VCFPActivity] onClientChosen: %s", device.name);
        PlexClient clientSelected = (PlexClient) device;

        setClient(clientSelected);

        // Start animating the action bar icon
        final MenuItem castIcon = menu.findItem(R.id.action_cast);
        castIcon.setIcon(R.drawable.mr_ic_media_route_connecting_holo_dark);
        AnimationDrawable ad = (AnimationDrawable) castIcon.getIcon();
        ad.start();


        if (clientSelected.isCastClient) {
          if(VoiceControlForPlexApplication.getInstance().hasChromecast()) {
            mClient = clientSelected;
            Logger.d("[VCFPActivity] subscribing");
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

  protected void showWearPurchaseRequired() {
    showWearPurchase(R.string.wear_purchase_required, false);
  }

  protected void showWearPurchase() {
    showWearPurchase(R.string.wear_detected_can_purchase, true);
  }

  protected void showWearPurchase(boolean showPurchaseFromMenu) {
    showWearPurchase(R.string.wear_detected_can_purchase, showPurchaseFromMenu);
  }

  protected void showWearPurchase(int stringResource, final boolean showPurchaseFromMenu) {
    new AlertDialog.Builder(VCFPActivity.this)
            .setMessage(String.format(getString(stringResource), VoiceControlForPlexApplication.getWearPrice()))
            .setCancelable(false)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.HAS_SHOWN_WEAR_PURCHASE_POPUP, true);
                dialogInterface.cancel();
                VoiceControlForPlexApplication.getInstance().getIabHelper().launchPurchaseFlow(VCFPActivity.this,
                        VoiceControlForPlexApplication.SKU_WEAR, 10001, mPurchaseFinishedListener,
                        VoiceControlForPlexApplication.SKU_TEST_PURCHASED == VoiceControlForPlexApplication.SKU_WEAR ? VoiceControlForPlexApplication.getInstance().getEmailHash() : "");
              }
            })
            .setNeutralButton(R.string.no_thanks, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.HAS_SHOWN_WEAR_PURCHASE_POPUP, true);
                if(showPurchaseFromMenu) {
                  new AlertDialog.Builder(VCFPActivity.this)
                          .setMessage(R.string.wear_purchase_from_menu)
                          .setCancelable(false)
                          .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                              dialog.cancel();
                            }
                          }).create().show();
                }
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
      } else if(purchase.getSku().equals(VoiceControlForPlexApplication.SKU_WEAR)) {
        Logger.d("Purchased Wear Support!");
        VoiceControlForPlexApplication.getInstance().setHasWear(true);
        hidePurchaseWearMenuItem();
        // Send a message to the wear device that wear support has been purchased
        new SendToDataLayerThread(WearConstants.WEAR_PURCHASED, VCFPActivity.this).start();
      }
    }
  };

  protected void hidePurchaseWearMenuItem() {
  }

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
      setCastIconInactive();
    } else {
      setCastIconActive();
    }
    networkMonitor.register();
	}

  @Override
  public void onSubscribed(PlexClient _client) {
    Logger.d("VCFPActivity: onSubscribed: %s", _client);
    mClient = _client;

    VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SUBSCRIBED_CLIENT, gsonWrite.toJson(_client));

		subscribing = false;
    try {
      setCastIconActive();
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
//    castPlayerManager.getPlaybackState();
  }

  @Override
  public void onCastConnectionFailed() {
    Logger.d("[VCFPActivity] onCastConnectionFailed");
    setCastIconActive();
    if(castPlayerManager.mClient != null)
      feedback.e(getString(R.string.couldnt_connect_to), castPlayerManager.mClient.name);
    subscribing = false;
  }

  @Override
  public void onCastSeek() {
    
  }

  @Override
  public void onCastPlayerState(PlayerState state, PlexMedia media) {
    mCurrentState = state;
    Logger.d("[VCFPActivity] mCurrentState: %s, media: %s", mCurrentState, media);
    if(!mCurrentState.equals(PlayerState.STOPPED) && media != null) {
      nowPlayingMedia = media;
      castPlayerManager.setNowPlayingMedia(nowPlayingMedia);
      mClient = castPlayerManager.mClient;
      VoiceControlForPlexApplication.getInstance().setNotification(mClient, mCurrentState, nowPlayingMedia);
    }
    sendWearPlaybackChange();
  }

  @Override
  public void onGetDeviceCapabilities(Capabilities capabilities) {
    if(mClient.isCastClient) {
      mClient.isAudioOnly = !capabilities.displaySupported;
      setClient(mClient);
    }
  }

  @Override
  public void onCastDisconnected() {
    Logger.d("[VCFPActivity] onCastDisconnected");
    onUnsubscribed();
  }

  @Override
  public void onCastPlayerStateChanged(PlayerState state) {
    PlayerState oldState = mCurrentState;
    mCurrentState = state;
    Logger.d("[VCFPActivity] onCastPlayerStateChanged: %s (old state: %s)", mCurrentState, oldState);
    if(mCurrentState != oldState) {
      if(mCurrentState == PlayerState.STOPPED) {
        VoiceControlForPlexApplication.getInstance().cancelNotification();
      } else {
        // TODO: only set notification here if it's already on?
        VoiceControlForPlexApplication.getInstance().setNotification(mClient, mCurrentState, nowPlayingMedia);
      }
      sendWearPlaybackChange();
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
          if(timeline.state == null)
            timeline.state = "stopped";
//          Logger.d("[VCFPActivity] onTimelineReceived: %s", timeline.state);
//          Logger.d("nowPlayingMedia: %s", nowPlayingMedia);
          // Get this media's info
          PlexServer server = null;
          for(PlexServer s : VoiceControlForPlexApplication.servers.values()) {
            if(s.machineIdentifier.equals(timeline.machineIdentifier)) {
              server = s;
              break;
            }
          }
          if((!timeline.state.equals("stopped") && nowPlayingMedia == null) || continuing) {
            // TODO: Might need to refresh server?
            if(server != null)
              getPlayingMedia(server, timeline);
          }

          if(nowPlayingMedia != null) {
//            Logger.d("timeline key: %s, now playing key: %s", timeline.key, nowPlayingMedia.key);
            if(timeline.key != null && timeline.key.equals(nowPlayingMedia.key)) {
              // Found an update for the currently playing media
              PlayerState oldState = mCurrentState;
              mCurrentState = PlayerState.getState(timeline.state);
              nowPlayingMedia.viewOffset = Integer.toString(timeline.time);
              if(oldState != mCurrentState) {
                sendWearPlaybackChange();
                if(mCurrentState == PlayerState.PLAYING) {
                  Logger.d("mClient is now playing");
                } else if(mCurrentState == PlayerState.PAUSED) {
                  Logger.d("mClient is now paused");
                } else if(mCurrentState == PlayerState.STOPPED) {
                  Logger.d("mClient is now stopped");
                  if(!continuing) {
                    VoiceControlForPlexApplication.getInstance().cancelNotification();
                    nowPlayingMedia = null;
                  }
                }
                if(mCurrentState != PlayerState.STOPPED && oldState != mCurrentState && VoiceControlForPlexApplication.getInstance().getNotificationStatus() != VoiceControlForPlexApplication.NOTIFICATION_STATUS.initializing) {
                  Logger.d("onTimelineReceived setting notification with %s", mCurrentState);
                  VoiceControlForPlexApplication.getInstance().setNotification(mClient, mCurrentState, nowPlayingMedia);
                }
              }
            } else if(timeline.key != null) {
              // A different piece of media is playing
              getPlayingMedia(server, timeline);
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
    Logger.d("[VCFPActivity] onUnsubscribed");
    setCastIconInactive();
    nowPlayingMedia = null;
    VoiceControlForPlexApplication.getInstance().cancelNotification();
    VoiceControlForPlexApplication.getInstance().prefs.remove(Preferences.SUBSCRIBED_CLIENT);
    sendWearPlaybackChange();
    feedback.m(R.string.disconnected);
  }

  private void getThumb(final String thumb, final PlexMedia media) {
    media.server.findServerConnection(new ActiveConnectionHandler() {
      @Override
      public void onSuccess(Connection connection) {
        String url = String.format("http://%s:%s%s", connection.address, connection.port, thumb);
        if(media.server.accessToken != null)
          url += String.format("?%s=%s", PlexHeaders.XPlexToken, media.server.accessToken);

        PlexHttpClient.getThumb(url, new InputStreamHandler() {
          @Override
          public void onSuccess(InputStream is) {
            try {
              InputStream iss = new ByteArrayInputStream(IOUtils.toByteArray(is));
              iss.reset();
              mSimpleDiskCache.put(media.getCacheKey(thumb), iss);
              setThumb(iss);
            } catch (IOException e) {
              Logger.d("Exception getting/saving thumb");
              e.printStackTrace();
            }
          }
        });
      }

      @Override
      public void onFailure(int statusCode) {

      }
    });
  }

  protected int getOrientation() {
    return getResources().getConfiguration().orientation;
  }

  @SuppressWarnings("deprecation")
  private void setThumb(InputStream is) {
    Logger.d("Setting thumb: %s", is);
    final View layout;
    View backgroundLayout = findViewById(R.id.background);
    View musicLayout = findViewById(R.id.nowPlayingMusicCover);
    if(nowPlayingMedia instanceof PlexTrack && getOrientation() == Configuration.ORIENTATION_PORTRAIT) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
        backgroundLayout.setBackground(null);
      else
        backgroundLayout.setBackgroundDrawable(null);
      layout = musicLayout;
    } else {
      if(musicLayout != null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
          musicLayout.setBackground(null);
        else
          musicLayout.setBackgroundDrawable(null);
      layout = backgroundLayout;
    }

    try {
      is.reset();
    } catch (IOException e) {
      e.printStackTrace();
    }

		if(layout == null)
			return;
		
    final Drawable d = Drawable.createFromStream(is, "thumb");
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
          layout.setBackground(d);
        else
          layout.setBackgroundDrawable(d);
      }
    });
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
          ex.printStackTrace();
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
  public void onCastPlayerPlaylistAdvance(PlexMedia media) {

  }

  protected void setServer(PlexServer _server) {
  }

  protected void setClient(PlexClient _client) {
    Logger.d("[VCFPActivity] setClient");
    VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.CLIENT, gsonWrite.toJson(_client));
  }

  public void showPlexServers(ConcurrentHashMap<String, PlexServer> servers, final ScanHandler scanHandler) {
    isScanning = false;
    if(cancelScan) {
      cancelScan = false;
      return;
    }
//    if(searchDialog != null)
//      searchDialog.dismiss();
    if(deviceSelectDialog == null) {
      deviceSelectDialog = new Dialog(this);
    }
    deviceSelectDialog.setContentView(R.layout.server_select);
    deviceSelectDialog.setTitle("Select a Plex Server");
    deviceSelectDialog.show();

    final ListView serverListView = (ListView) deviceSelectDialog.findViewById(R.id.serverListView);
    if(servers == null)
      servers = new ConcurrentHashMap<String, PlexServer>(VoiceControlForPlexApplication.servers);
    final PlexListAdapter adapter = new PlexListAdapter(this, PlexListAdapter.TYPE_SERVER);
    adapter.setServers(servers);
    serverListView.setAdapter(adapter);
    serverListView.setOnItemClickListener(new ListView.OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> parentAdapter, View view, int position, long id) {
        Logger.d("Clicked position %d", position);
        PlexServer s = (PlexServer)parentAdapter.getItemAtPosition(position);
        deviceSelectDialog.dismiss();
        scanHandler.onDeviceSelected(s, false);
      }
    });
  }

  public void showPlexClients() {
    showPlexClients(false, null);
  }

  public void showPlexClients(boolean showResume) {
    showPlexClients(true, null);
  }

  public void showPlexClients(boolean showResume, final ScanHandler onFinish) {
    isScanning = false;
    if(cancelScan) {
      cancelScan = false;
      return;
    }
    if (deviceSelectDialog == null) {
      deviceSelectDialog = new Dialog(this);
    }
    deviceSelectDialog.setContentView(R.layout.server_select);
    deviceSelectDialog.setTitle(R.string.select_plex_client);
    deviceSelectDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialogInterface) {
        Logger.d("[VCFPActivity] setting clientScanCanceled to true");
        clientScanCanceled = true;
        subscribing = false;
      }
    });
    deviceSelectDialog.show();

    if (showResume) {
      CheckBox resumeCheckbox = (CheckBox) deviceSelectDialog.findViewById(R.id.serverListResume);
      resumeCheckbox.setVisibility(View.VISIBLE);
    }

    final ListView clientListView = (ListView) deviceSelectDialog.findViewById(R.id.serverListView);
    final PlexListAdapter adapter = new PlexListAdapter(this, PlexListAdapter.TYPE_CLIENT);
    adapter.setClients(VoiceControlForPlexApplication.getAllClients());
    clientListView.setAdapter(adapter);
    clientListView.setOnItemClickListener(new ListView.OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> parentAdapter, View view, int position,
                              long id) {
        PlexClient s = (PlexClient) parentAdapter.getItemAtPosition(position);
        deviceSelectDialog.dismiss();
        CheckBox resumeCheckbox = (CheckBox) deviceSelectDialog.findViewById(R.id.serverListResume);
//        if (onFinish == null)
//          scanHandler.onDeviceSelected(s, resumeCheckbox.isChecked());
//        else
        if (onFinish != null)
          onFinish.onDeviceSelected(s, resumeCheckbox.isChecked());
      }

    });
  }

  protected void searchForPlexClients() {
    searchForPlexClients(false);
  }

  protected void searchForPlexClients(boolean connectToClient) {
    Intent scannerIntent = new Intent(VCFPActivity.this, PlexScannerService.class);
    scannerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    scannerIntent.putExtra(PlexScannerService.CLASS, MainActivity.class);
    scannerIntent.putExtra(com.atomjack.shared.Intent.EXTRA_CONNECT_TO_CLIENT, connectToClient);
    scannerIntent.setAction(PlexScannerService.ACTION_SCAN_CLIENTS);
    startService(scannerIntent);
  }

  public void deviceSelectDialogRefresh() {
    ListView serverListView = (ListView) deviceSelectDialog.findViewById(R.id.serverListView);
    PlexListAdapter adapter = (PlexListAdapter)serverListView.getAdapter();
    adapter.setClients(VoiceControlForPlexApplication.getAllClients());
    adapter.notifyDataSetChanged();
  }


  public void sendWearPlaybackChange() {
    if(VoiceControlForPlexApplication.getInstance().hasWear()) {
      Logger.d("[VCFPActivity] subscribed: %s", plexSubscription.isSubscribed());
      if(!plexSubscription.isSubscribed() && !castPlayerManager.isSubscribed()) {
        new SendToDataLayerThread(WearConstants.DISCONNECTED, this).start();
      } else {
        Logger.d("[VCFPActivity] Sending Wear Notification: %s", mCurrentState);
        final DataMap data = new DataMap();
        String msg = null;
        if (mCurrentState == PlayerState.PLAYING) {
          data.putString(WearConstants.MEDIA_TYPE, nowPlayingMedia.getType());
          VoiceControlForPlexApplication.SetWearMediaTitles(data, nowPlayingMedia);
          msg = WearConstants.MEDIA_PLAYING;
        } else if (mCurrentState == PlayerState.STOPPED) {
          msg = WearConstants.MEDIA_STOPPED;
        } else if (mCurrentState == PlayerState.PAUSED) {
          msg = WearConstants.MEDIA_PAUSED;
          VoiceControlForPlexApplication.SetWearMediaTitles(data, nowPlayingMedia);
        }
        if (msg != null) {
          if (msg.equals(WearConstants.MEDIA_PLAYING)) {
            VoiceControlForPlexApplication.getWearMediaImage(nowPlayingMedia, new BitmapHandler() {
              @Override
              public void onSuccess(Bitmap bitmap) {
                DataMap binaryDataMap = new DataMap();
                binaryDataMap.putAll(data);
                binaryDataMap.putAsset(WearConstants.IMAGE, VoiceControlForPlexApplication.createAssetFromBitmap(bitmap));
                binaryDataMap.putString(WearConstants.PLAYBACK_STATE, mCurrentState.name());
                new SendToDataLayerThread(WearConstants.RECEIVE_MEDIA_IMAGE, binaryDataMap, VCFPActivity.this).sendDataItem();
              }
            });
          }
          new SendToDataLayerThread(msg, data, this).start();
        }
      }
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    Logger.d("[VCFPActivity] onNewIntent: %s", intent.getAction());
    if(intent.getAction() != null) {
      if(intent.getAction().equals(com.atomjack.shared.Intent.ACTION_PAUSE) || intent.getAction().equals(com.atomjack.shared.Intent.ACTION_PLAY)) {
        VoiceControlForPlexApplication.getInstance().plexSubscription.getListener().doPlayPause();
      }
    }
  }

  public PlexMedia getNowPlayingMedia() {
    return nowPlayingMedia;
  }

  public void doPlayPause() {}
  public void doPlayPause(View v) {}

  public PlexClient getClient() {
    return mClient;
  }
}
