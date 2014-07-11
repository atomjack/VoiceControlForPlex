package com.atomjack.vcfp.activities;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
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

  protected boolean continuing = false;

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

  protected PlexClient postChromecastPurchaseClient = null;
  protected Runnable postChromecastPurchaseAction = null;

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

    castPlayerManager = VoiceControlForPlexApplication.getInstance().castPlayerManager;
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
						Logger.d("We're watching %s", nowPlayingMedia.title);

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
        Logger.d("subscribed: %s", subscribed);
        Logger.d("subscribing: %s", subscribing);
				if(!isSubscribed() && !subscribing) {
					subscribing = true;
          if(VoiceControlForPlexApplication.clients.size() == 0 || VoiceControlForPlexApplication.hasDoneClientScan) {
            localScan.searchForPlexClients(true);
          } else
  					localScan.showPlexClients(false, onClientChosen);
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


        if (clientSelected.isCastClient) {
          if(VoiceControlForPlexApplication.getInstance().hasChromecast()) {
            mClient = clientSelected;
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

  @Override
  public void onSubscribeError(String errorMessage) {
    feedback.e(String.format(getString(R.string.got_error), errorMessage));
    setCastIconInactive();
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
        if (t.key != null) {

          if((!t.state.equals("stopped") && nowPlayingMedia == null) || continuing) {
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
              PlayerState oldState = mCurrentState;
              mCurrentState = PlayerState.getState(t.state);
              nowPlayingMedia.viewOffset = Integer.toString(t.time);
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
                  Logger.d("onMessageReceived setting notification with %s", mCurrentState);
                  VoiceControlForPlexApplication.getInstance().setNotification(mClient, mCurrentState, nowPlayingMedia);
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
    setCastIconInactive();
    subscribed = false;
    nowPlayingMedia = null;
    mNotifyMgr.cancel(mNotificationId);
    feedback.m(R.string.disconnected);
  }

  protected void setCastIconInactive() {
    MenuItem castIcon = menu.findItem(R.id.action_cast);
    castIcon.setIcon(R.drawable.mr_ic_media_route_holo_dark);
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
    View musicLayout = findViewById(R.id.nowPlayingMusicCover);;

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
    Logger.d("d: %s", d);
//    d.setAlpha(80);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
      layout.setBackground(d);
    else
      layout.setBackgroundDrawable(d);
  }

  public void setThumb() {
    if(!nowPlayingMedia.thumb.equals("")) {
      String thumb = nowPlayingMedia.thumb;
      if(nowPlayingMedia instanceof PlexVideo) {
        PlexVideo video = (PlexVideo)nowPlayingMedia;
        thumb = video.isMovie() ? video.thumb : video.grandparentThumb;
        Logger.d("orientation: %s, type: %s", getOrientation(), video.type);
        if(getOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
          if(video.isMovie())
            thumb = video.art;
          else if(video.isShow()) {
            thumb = video.art;
          }
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
}
