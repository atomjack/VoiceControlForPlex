package com.atomjack.vcfp;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import com.android.vending.billing.IabHelper;
import com.android.vending.billing.IabResult;
import com.android.vending.billing.Inventory;
import com.android.vending.billing.Purchase;
import com.android.vending.billing.SkuDetails;
import com.atomjack.shared.Intent;
import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.Preferences;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.UriDeserializer;
import com.atomjack.shared.UriSerializer;
import com.atomjack.shared.WearConstants;
import com.atomjack.vcfp.activities.CastActivity;
import com.atomjack.vcfp.activities.MainActivity;
import com.atomjack.vcfp.activities.NowPlayingActivity;
import com.atomjack.vcfp.interfaces.ActiveConnectionHandler;
import com.atomjack.vcfp.interfaces.BitmapHandler;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.services.PlexControlService;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import cz.fhucho.android.util.SimpleDiskCache;
import retrofit.Call;
import retrofit.Callback;
import retrofit.Response;

public class VoiceControlForPlexApplication extends Application
{
	public final static String MINIMUM_PHT_VERSION = "1.0.7";

	private static boolean isApplicationVisible;

  private static int nowPlayingNotificationId = 0;

  private static VoiceControlForPlexApplication instance;

  public Preferences prefs;

	public static Gson gsonRead = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriDeserializer())
					.create();

	public static Gson gsonWrite = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriSerializer())
					.create();

  private NOTIFICATION_STATUS notificationStatus = NOTIFICATION_STATUS.off;
  public enum NOTIFICATION_STATUS {
    off,
    on,
    initializing
  }

  public static HashMap<String, String[]> chromecastVideoOptions = new LinkedHashMap<>();

  private NotificationManager mNotifyMgr;
  private Bitmap notificationBitmap = null;
  private Bitmap notificationBitmapBig = null;

	public static ConcurrentHashMap<String, PlexServer> servers = new ConcurrentHashMap<String, PlexServer>();
	public static Map<String, PlexClient> clients = new HashMap<String, PlexClient>();
	public static Map<String, PlexClient> castClients = new HashMap<String, PlexClient>();

	private static Serializer serial = new Persister();

  public PlexSubscription plexSubscription;
  public CastPlayerManager castPlayerManager;
  public SimpleDiskCache mSimpleDiskCache;

  private NetworkChangeListener networkChangeListener;

  // In-app purchasing
  private IabHelper mIabHelper;
  // TODO: Obfuscate this somehow:
  String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlgV+Gdi4nBVn2rRqi+oVLhenzbWcEVyUf1ulhvAElEf6c8iuX3OB4JZRYVhCE690mFaYUdEb8OG8p8wT7IrQmlZ0DRfP2X9csBJKd3qB+l9y11Ggujivythvoiz+uvDPhz54O6wGmUB8+oZXN+jk9MT5Eia3BZxJDvgFcmDe/KQTTKZoIk1Qs/4PSYFP8jaS/lc71yDyRmvAM+l1lv7Ld8h69hVvKFUr9BT/20lHQGohCIc91CJvKIP5DaptbE98DAlrTxjZRRpbi+wrLGKVbJpUOBgPC78qo3zPITn6M6N0tHkv1tHkGOeyLUbxOC0wFdXj33mUldV/rp3tHnld1wIDAQAB";
  private boolean inventoryQueried = false;

  // Has the user purchased chromecast/wear support?
  // This is the default value.
  private boolean mHasChromecast = !BuildConfig.CHROMECAST_REQUIRES_PURCHASE;
  private boolean mHasWear = !BuildConfig.WEAR_REQUIRES_PURCHASE;
  // Only the release build will use the actual Chromecast SKU
  public static final String SKU_CHROMECAST = BuildConfig.SKU_CHROMECAST;
  public static final String SKU_WEAR = BuildConfig.SKU_WEAR;
  public static final String SKU_TEST_PURCHASED = "android.test.purchased";
  private static String mChromecastPrice = "$0.99"; // Default price, just in case
  private static String mWearPrice = "$2.00"; // Default price, just in case

  public static boolean hasDoneClientScan = false;

  GoogleApiClient googleApiClient;

  // This is needed so that we can let the main activity know that wear support is enabled, after querying the inventory from Google
  private MainActivity mainActivity;

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;

    googleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .build();
    googleApiClient.connect();

    prefs = new Preferences(getApplicationContext());

    plexSubscription = new PlexSubscription();
    castPlayerManager = new CastPlayerManager(getApplicationContext());

//    chromecastVideoOptions.put(getString(R.string.original), new String[]{"12000", "1920x1080", "1"}); // Disabled for now. Don't know how to get PMS to direct play to chromecast
    chromecastVideoOptions.put("20mbps 720p", new String[]{"20000", "1280x720"});
    chromecastVideoOptions.put("12mbps 720p", new String[]{"12000", "1280x720"});
    chromecastVideoOptions.put("10mbps 720p", new String[]{"10000", "1280x720"});
    chromecastVideoOptions.put("8mbps 720p", new String[]{"8000", "1280x720"});
    chromecastVideoOptions.put("4mbps 720p", new String[]{"4000", "1280x720"});
    chromecastVideoOptions.put("3mbps 720p", new String[]{"3000", "1280x720"});
    chromecastVideoOptions.put("2mbps 720p", new String[]{"2000", "1280x720"});
    chromecastVideoOptions.put("1.5mbps 720p", new String[]{"1500", "1280x720"});

    if(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.CHROMECAST_VIDEO_QUALITY_LOCAL) == null)
      VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.CHROMECAST_VIDEO_QUALITY_LOCAL, "8mbps 720p");
    if(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.CHROMECAST_VIDEO_QUALITY_REMOTE) == null)
      VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.CHROMECAST_VIDEO_QUALITY_REMOTE, "8mbps 720p");


    // Check for donate version, and if found, allow chromecast & wear
    PackageInfo pinfo;
    try
    {
      pinfo = getPackageManager().getPackageInfo("com.atomjack.vcfpd", 0);
      mHasChromecast = true;
      mHasWear = true;
    } catch(Exception e) {}

    // If this build includes chromecast and wear support, no need to setup purchasing
    if(!mHasChromecast || !mHasWear)
      setupInAppPurchasing();

    mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    // Load saved clients and servers
    Type clientType = new TypeToken<HashMap<String, PlexClient>>(){}.getType();
    VoiceControlForPlexApplication.castClients = gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SAVED_CAST_CLIENTS, "{}"), clientType);
    VoiceControlForPlexApplication.clients = gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SAVED_CLIENTS, "{}"), clientType);
    Type serverType = new TypeToken<ConcurrentHashMap<String, PlexServer>>(){}.getType();
    VoiceControlForPlexApplication.servers = gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SAVED_SERVERS, "{}"), serverType);

    try {
      PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
      mSimpleDiskCache = SimpleDiskCache.open(getCacheDir(), pInfo.versionCode, Long.parseLong(Integer.toString(10 * 1024 * 1024)));
      Logger.d("Cache initialized");
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public boolean hasChromecast() {
    return mHasChromecast;
  }

  public boolean hasWear() {
    return mHasWear;
  }

  public static VoiceControlForPlexApplication getInstance() {
    return instance;
  }

	public static Locale getVoiceLocale(String loc) {
		String[] voice = loc.split("-");

		Locale l = null;
		if(voice.length == 1)
			l = new Locale(voice[0]);
		else if(voice.length == 2)
			l = new Locale(voice[0], voice[1]);
		else if(voice.length == 3)
			l = new Locale(voice[0], voice[1], voice[2]);

		return l;
	}

  public static void addPlexServer(final PlexServer server) {
		addPlexServer(server, null);
	}

	public static void addPlexServer(PlexServer addedServer, final Runnable onFinish) {
		Logger.d("ADDING PLEX SERVER: %s, %s", addedServer.name, addedServer.address);
		if(addedServer.name.equals("") || addedServer.address.equals("")) {
			return;
		}
    PlexServer serverToAdd = null;
    // First, see if we've already found this server from a remote scan. We'll want to use that one instead so the remote connections are included
    if(onFinish == null) { // onFinish is null when called from a local server scan - otherwise it's from a remote scan
      for (PlexServer _server : VoiceControlForPlexApplication.servers.values()) {
        if (_server.machineIdentifier.equals(addedServer.machineIdentifier)) {
          serverToAdd = _server;
          break;
        }
      }
    }
    final PlexServer server = serverToAdd == null ? addedServer : serverToAdd;
    try {
      server.findServerConnection(new ActiveConnectionHandler() {
        @Override
        public void onSuccess(Connection connection) {
          Logger.d("active connection: %s", connection);
          PlexHttpClient.PlexHttpService service = PlexHttpClient.getService(connection);
          Call<MediaContainer> call = service.getLibrarySections(server.accessToken);
          call.enqueue(new Callback<MediaContainer>() {
            @Override
            public void onResponse(Response<MediaContainer> response) {
              MediaContainer mc = response.body();
              server.movieSections = new ArrayList<String>();
              server.tvSections = new ArrayList<String>();
              server.musicSections = new ArrayList<String>();
              for(int i=0;i<mc.directories.size();i++) {
                if(mc.directories.get(i).type.equals("movie")) {
                  server.addMovieSection(mc.directories.get(i).key);
                }
                if(mc.directories.get(i).type.equals("show")) {
                  server.addTvSection(mc.directories.get(i).key);
                }
                if(mc.directories.get(i).type.equals("artist")) {
                  server.addMusicSection(mc.directories.get(i).key);
                }
              }
              Logger.d("%s has %d directories.", server.name, mc.directories != null ? mc.directories.size() : 0);
              Logger.d("Servers: %s", servers);
              if(!server.name.equals("")) {
                servers.put(server.name, server);

                // Finally, if this server is the current default server, save it in preferences so the access token gets transferred
                PlexServer defaultServer = gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SERVER, ""), PlexServer.class);
                if(defaultServer != null && server.machineIdentifier.equals(defaultServer.machineIdentifier)) {
                  VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SERVER, gsonWrite.toJson(server));
                }

                Logger.d("Added %s.", server.name);
              }
              if(onFinish != null)
                onFinish.run();
            }

            @Override
            public void onFailure(Throwable t) {
              if(onFinish != null)
                onFinish.run();
            }
          });
        }

        @Override
        public void onFailure(int statusCode) {
          Logger.d("Failed to find connection for %s: %d", server.name, statusCode);
          if(onFinish != null)
            onFinish.run();
        }
      });


    } catch (Exception e) {
      Logger.e("Exception getting clients: %s", e.toString());
      if(onFinish != null)
        onFinish.run();
    }
	}

	public static boolean isVersionLessThan(String v1, String v2) {
		VersionComparator cmp = new VersionComparator();
		return cmp.compare(v1, v2) < 0;
	}

	public static boolean isWifiConnected(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		return mWifi.isConnected();
	}

	public static void showNoWifiDialog(Context context) {
		AlertDialog.Builder usageDialog = new AlertDialog.Builder(context);
		usageDialog.setTitle(R.string.no_wifi_connection);
		usageDialog.setMessage(R.string.no_wifi_connection_message);
		usageDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		usageDialog.show();
	}

	public static boolean isApplicationVisible() {
		return isApplicationVisible;
	}

	public static void applicationResumed() {
		isApplicationVisible = true;
	}

	public static void applicationPaused() {
		isApplicationVisible = false;
	}

	public static String secondsToTimecode(double _seconds) {
		ArrayList<String> timecode = new ArrayList<String>();
		double hours, minutes, seconds = 0;
		hours = Math.floor(_seconds/3600);
		minutes = Math.floor((_seconds - (hours * 3600)) / 60);
		seconds = Math.floor(_seconds - (hours * 3600) - (minutes * 60));
		if(hours > 0)
			timecode.add(twoDigitsInt((int)hours));
		timecode.add(twoDigitsInt((int)minutes));
		timecode.add(twoDigitsInt((int) seconds));
		return TextUtils.join(":", timecode);
	}

	private static String twoDigitsInt( int pValue )
	{
		if ( pValue == 0 )
			return "00";
		if ( pValue < 10 )
			return "0" + pValue;

		return String.valueOf( pValue );
	}

  public static String generateRandomString() {
    SecureRandom random = new SecureRandom();
    return new BigInteger(130, random).toString(32).substring(0, 12);
  }

  public Bitmap getCachedBitmap(String key) {
    if(key == null)
      return null;

    Bitmap bitmap = null;
    try {
//      Logger.d("Trying to get cached thumb: %s", key);
      SimpleDiskCache.BitmapEntry bitmapEntry = mSimpleDiskCache.getBitmap(key);
//      Logger.d("bitmapEntry: %s", bitmapEntry);
      if(bitmapEntry != null) {
        bitmap = bitmapEntry.getBitmap();
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    return bitmap;


  }

  // Fetch the notification bitmap for the given key. Once it's been downloaded, we'll save the bitmap to the image cache, then set the
  // notification again.
  private void fetchNotificationBitmap(final PlexMedia.IMAGE_KEY key, final PlexClient client, final PlexMedia media, final PlayerState currentState) {
      Logger.d("Thumb not found in cache. Downloading %s.", key);
      new AsyncTask() {
        @Override
        protected Object doInBackground(Object[] objects) {
          if (client != null && media != null) {
            InputStream inputStream = media.getNotificationThumb(media instanceof PlexTrack ? PlexMedia.IMAGE_KEY.NOTIFICATION_THUMB_MUSIC : key);
            if(inputStream != null) {
              try {
                inputStream.reset();
              } catch (IOException e) {
              }
              try {
                Logger.d("image key: %s", media.getImageKey(key));
                mSimpleDiskCache.put(media.getImageKey(key), inputStream);
                inputStream.close();
                Logger.d("Downloaded thumb. Redoing notification.");
                setNotification(client, currentState, media, true);
              } catch (Exception e) {
              }
            }
          }
          return null;
        }
      }.execute();
  }

  public void fetchNotificationBitmap(final PlexMedia.IMAGE_KEY key, final PlexMedia media, final Runnable onFinish) {
    new AsyncTask() {
      @Override
      protected Object doInBackground(Object[] params) {
        InputStream inputStream = media.getNotificationThumb(key);
        if(inputStream != null) {
          try {
            inputStream.reset();
          } catch (IOException e) {
          }
          try {
            Logger.d("image key: %s", media.getImageKey(key));
            mSimpleDiskCache.put(media.getImageKey(key), inputStream);
            inputStream.close();
            if(onFinish != null)
              onFinish.run();
          } catch (Exception e) {
          }
        }
        return null;
      }
    }.execute();
  }

  public void setNotification(final PlexClient client, final PlayerState currentState, final PlexMedia media) {
    setNotification(client, currentState, media, false);
  }

  public void setNotification(final PlexClient client, final PlayerState currentState, final PlexMedia media, boolean skipThumb) {
    if(client == null) {
      Logger.d("Client is null for some reason");
      return;
    }
    if(client.isLocalDevice())
      return;
    Logger.d("Setting notification, client: %s, media: %s", client, media);
    if(notificationStatus == NOTIFICATION_STATUS.off) {
      notificationStatus = NOTIFICATION_STATUS.initializing;
      notificationBitmap = null;
      notificationBitmapBig = null;
    }

    notificationBitmap = getCachedBitmap(media.getImageKey(PlexMedia.IMAGE_KEY.NOTIFICATION_THUMB));
    if(notificationBitmap == null && notificationStatus == NOTIFICATION_STATUS.initializing && !skipThumb)
      fetchNotificationBitmap(PlexMedia.IMAGE_KEY.NOTIFICATION_THUMB, client, media, currentState);
    notificationBitmapBig = getCachedBitmap(media.getImageKey(PlexMedia.IMAGE_KEY.NOTIFICATION_THUMB_BIG));
    if(notificationBitmapBig == null && notificationStatus == NOTIFICATION_STATUS.initializing && !skipThumb)
      fetchNotificationBitmap(PlexMedia.IMAGE_KEY.NOTIFICATION_THUMB_BIG, client, media, currentState);

    android.content.Intent rewindIntent = new android.content.Intent(VoiceControlForPlexApplication.this, PlexControlService.class);
    rewindIntent.setAction(PlexControlService.ACTION_REWIND);
    rewindIntent.putExtra(PlexControlService.CLIENT, client);
    rewindIntent.putExtra(PlexControlService.MEDIA, media);
    PendingIntent piRewind = PendingIntent.getService(VoiceControlForPlexApplication.this, 0, rewindIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    android.content.Intent forwardIntent = new android.content.Intent(VoiceControlForPlexApplication.this, PlexControlService.class);
    forwardIntent.setAction(PlexControlService.ACTION_FORWARD);
    forwardIntent.putExtra(PlexControlService.CLIENT, client);
    forwardIntent.putExtra(PlexControlService.MEDIA, media);
    PendingIntent piForward = PendingIntent.getService(VoiceControlForPlexApplication.this, 0, forwardIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    android.content.Intent playIntent = new android.content.Intent(VoiceControlForPlexApplication.this, PlexControlService.class);
    playIntent.setAction(PlexControlService.ACTION_PLAY);
    playIntent.putExtra(PlexControlService.CLIENT, client);
    playIntent.putExtra(PlexControlService.MEDIA, media);
    PendingIntent playPendingIntent = PendingIntent.getService(VoiceControlForPlexApplication.this, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    android.content.Intent pauseIntent = new android.content.Intent(VoiceControlForPlexApplication.this, PlexControlService.class);
    pauseIntent.setAction(PlexControlService.ACTION_PAUSE);
    pauseIntent.putExtra(PlexControlService.CLIENT, client);
    pauseIntent.putExtra(PlexControlService.MEDIA, media);
    PendingIntent pausePendingIntent = PendingIntent.getService(VoiceControlForPlexApplication.this, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    android.content.Intent disconnectIntent = new android.content.Intent(VoiceControlForPlexApplication.this, PlexControlService.class);
    disconnectIntent.setAction(PlexControlService.ACTION_DISCONNECT);
    disconnectIntent.putExtra(PlexControlService.CLIENT, client);
    disconnectIntent.putExtra(PlexControlService.MEDIA, media);
    PendingIntent piDisconnect = PendingIntent.getService(VoiceControlForPlexApplication.this, 0, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    android.content.Intent nowPlayingIntent;
    if(client.isCastClient) {
      nowPlayingIntent = new android.content.Intent(VoiceControlForPlexApplication.this, CastActivity.class);
    } else
      nowPlayingIntent = new android.content.Intent(VoiceControlForPlexApplication.this, NowPlayingActivity.class);
    nowPlayingIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
    nowPlayingIntent.putExtra(Intent.EXTRA_MEDIA, media);
    nowPlayingIntent.putExtra(Intent.EXTRA_CLIENT, client);
    PendingIntent piNowPlaying = PendingIntent.getActivity(VoiceControlForPlexApplication.this, 0, nowPlayingIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    try {
      NotificationCompat.Builder mBuilder =
              new NotificationCompat.Builder(VoiceControlForPlexApplication.this)
                      .setSmallIcon(R.drawable.ic_launcher)
                      .setAutoCancel(false)
                      .setOngoing(true)
                      .setOnlyAlertOnce(true)
                      .setContentIntent(piNowPlaying)
                      .setContent(getNotificationView(
                              media.isMusic() ? R.layout.now_playing_notification_music : R.layout.now_playing_notification,
                              notificationBitmap, media, client, playPendingIntent, pausePendingIntent,
                              piRewind, piForward, piDisconnect, currentState == PlayerState.PLAYING))
                      .setDefaults(Notification.DEFAULT_ALL);
      Notification n = mBuilder.build();
      if (Build.VERSION.SDK_INT >= 16)
        n.bigContentView = getNotificationView(
                media.isMusic() ? R.layout.now_playing_notification_big_music : R.layout.now_playing_notification_big,
                notificationBitmapBig, media, client, playPendingIntent, pausePendingIntent,
                piRewind, piForward, piDisconnect, currentState == PlayerState.PLAYING);

      // Disable notification sound
      n.defaults = 0;
      mNotifyMgr.notify(nowPlayingNotificationId, n);
      notificationStatus = NOTIFICATION_STATUS.on;
      Logger.d("Notification set");

//      sendWearNotification(media);
    } catch (Exception ex) {
      ex.printStackTrace();
    }


  }

  public void cancelNotification() {
    mNotifyMgr.cancel(nowPlayingNotificationId);
//    if(hasWear()) {
//      new SendToDataLayerThread(WearConstants.MEDIA_STOPPED, this).start();
//    }
    notificationStatus = NOTIFICATION_STATUS.off;
  }

  public NOTIFICATION_STATUS getNotificationStatus() {
    return notificationStatus;
  }

  private RemoteViews getNotificationView(int layoutId, Bitmap thumb, PlexMedia media, PlexClient client,
                                          PendingIntent playPendingIntent, PendingIntent pausePendingIntent, PendingIntent rewindIntent,
                                          PendingIntent forwardIntent, PendingIntent disconnectIntent, boolean isPlaying) {
    RemoteViews remoteViews = new RemoteViews(getPackageName(), layoutId);
    remoteViews.setImageViewBitmap(R.id.thumb, thumb);
    String title = media.title; // Movie title
    if(media.isMusic())
      title = String.format("%s - %s", media.grandparentTitle, media.title);
    else if(media.isShow())
      title = String.format("%s - %s", media.grandparentTitle, media.title);
//    else if(media.isShow())
//      title = String.format("%s - %s", media.grandparentTitle, media.title);
    remoteViews.setTextViewText(R.id.title, title);
    remoteViews.setTextViewText(R.id.playingOn, String.format(getString(R.string.playing_on), client.name));

    remoteViews.setOnClickPendingIntent(R.id.pauseButton, pausePendingIntent);
    remoteViews.setOnClickPendingIntent(R.id.playButton, playPendingIntent);
    if (isPlaying) {
      remoteViews.setViewVisibility(R.id.playButton, View.GONE);
      remoteViews.setViewVisibility(R.id.pauseButton, View.VISIBLE);
    } else {
      remoteViews.setViewVisibility(R.id.playButton, View.VISIBLE);
      remoteViews.setViewVisibility(R.id.pauseButton, View.GONE);
    }
    remoteViews.setOnClickPendingIntent(R.id.rewindButton, rewindIntent);
    remoteViews.setOnClickPendingIntent(R.id.forwardButton, forwardIntent);
    remoteViews.setOnClickPendingIntent(R.id.disconnectButton, disconnectIntent);

    return remoteViews;
  }

  public void setupInAppPurchasing() {

    mIabHelper = new IabHelper(this, base64EncodedPublicKey);
    mIabHelper.enableDebugLogging(false);

    mIabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
      public void onIabSetupFinished(IabResult result) {
        Logger.d("Setup finished.");

        Logger.d("Hash: %s", getEmailHash());
        if (!result.isSuccess()) {
          // Oh noes, there was a problem.
          Logger.d("Problem setting up in-app billing: " + result);
          return;
        }



        // Have we been disposed of in the meantime? If so, quit.
        if (mIabHelper == null) return;

        // IAB is fully set up. Now, let's get an inventory of stuff we own.
        Logger.d("Setup successful. Querying inventory.");
        mIabHelper.queryInventoryAsync(mGotInventoryListener);
      }
    });
  }

  IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
    public void onQueryInventoryFinished(IabResult result, final Inventory inventory) {

      // Have we been disposed of in the meantime? If so, quit.
      if (mIabHelper == null) return;

      // Is it a failure?
      if (result.isFailure()) {
        Logger.d("Failed to query inventory: " + result);
        return;
      }

      Logger.d("Query inventory was successful.");

      // Get the price for chromecast & wear support
      mIabHelper.queryInventoryAsync(true, new ArrayList<String>(Arrays.asList(SKU_CHROMECAST, SKU_WEAR)), new IabHelper.QueryInventoryFinishedListener() {
        @Override
        public void onQueryInventoryFinished(IabResult result, Inventory inv) {
          SkuDetails skuDetails = inv.getSkuDetails(SKU_CHROMECAST);
          if(skuDetails != null) {
            mChromecastPrice = skuDetails.getPrice();
          }
          skuDetails = inv.getSkuDetails(SKU_WEAR);
          if(skuDetails != null) {
            mWearPrice = skuDetails.getPrice();
          }

          // If the SKU being used is the test sku, consume it so that it has to be bought each time the app is run
          if(SKU_CHROMECAST == SKU_TEST_PURCHASED || SKU_WEAR == SKU_TEST_PURCHASED) {
            if (inventory.hasPurchase(SKU_TEST_PURCHASED))
              mIabHelper.consumeAsync(inventory.getPurchase(SKU_TEST_PURCHASED),null);
          } else {
            Purchase chromecastPurchase = inventory.getPurchase(SKU_CHROMECAST);
            mHasChromecast = (chromecastPurchase != null && verifyDeveloperPayload(chromecastPurchase));

            Purchase wearPurchase = inventory.getPurchase(SKU_WEAR);
            mHasWear = (wearPurchase != null && verifyDeveloperPayload(wearPurchase));
          }

          Logger.d("Has Chromecast: %s", mHasChromecast);
          Logger.d("Has Wear: %s", mHasWear);
          Logger.d("Initial inventory query finished.");
          inventoryQueried = true;
          onInventoryQueryFinished();
        }
      });


    }
  };

  public boolean getInventoryQueried() {
    return inventoryQueried;
  }

  // This runs once we have queried the play store to see if chromecast or wear support have been purchased.
  // If Wear support has not been purchased, we can attempt to contact a connected Wear device, and if we hear back,
  // we can throw a popup alerting the user that the app supports Wear
  private void onInventoryQueryFinished() {
    if(!mHasWear) {
      Logger.d("[VoiceControlForPlexApplication] Sending ping");
      new SendToDataLayerThread(WearConstants.PING, this).start();
    } else {
      if(mainActivity != null) {
        mainActivity.hidePurchaseWearMenuItem();
      }
    }
  }

  public void setOnHasWearActivity(MainActivity activity) {
    mainActivity = activity;
  }

  boolean verifyDeveloperPayload(Purchase p) {
    return p.getDeveloperPayload().equals(SKU_TEST_PURCHASED == SKU_CHROMECAST ? getEmailHash() : "");
  }

  public String getEmailHash() {
    AccountManager manager = (AccountManager) getSystemService(ACCOUNT_SERVICE);
    Account[] list = manager.getAccounts();
    String userEmail = "";
    for(Account account : list) {
      if(account.type.equals("com.google")) {
        userEmail = account.name;
        break;
      }
    }
    String hash = "";
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.reset();
      byte[] buffer = userEmail.getBytes();
      md.update(buffer);
      byte[] digest = md.digest();

      for (int i = 0; i < digest.length; i++) {
        hash +=  Integer.toString( ( digest[i] & 0xff ) + 0x100, 16).substring( 1 );
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    Logger.d("hash: %s", hash);
    return hash;
  }

  public IabHelper getIabHelper() {
    return mIabHelper;
  }

  public void setHasChromecast(boolean hasChromecast) {
    mHasChromecast = hasChromecast;
  }

  public void setHasWear(boolean hasWear) {
    mHasWear = hasWear;
  }

  public static String getChromecastPrice() {
    return mChromecastPrice;
  }

  public static String getWearPrice() {
    return mWearPrice;
  }

  public static Map<String, PlexClient> getAllClients() {
    Map<String, PlexClient> allClients = new HashMap<String, PlexClient>();
    allClients.putAll(clients);
    allClients.putAll(castClients);
    return allClients;
  }

  public interface NetworkChangeListener {
    void onConnected(int connectionType);
    void onDisconnected();
  }

  public void onNetworkConnected(int connectionType) {
    if(networkChangeListener != null) {
      networkChangeListener.onConnected(connectionType);
    }
  }

  public void onNetworkDisconnected() {
    if(networkChangeListener != null)
    networkChangeListener.onDisconnected();
  }

  public void setNetworkChangeListener(NetworkChangeListener listener) {
    networkChangeListener = listener;
  }

  public static void getWearMediaImage(final PlexMedia media, final BitmapHandler onFinished) {
    Bitmap bitmap = VoiceControlForPlexApplication.getInstance().getCachedBitmap(media.getImageKey(PlexMedia.IMAGE_KEY.WEAR_BACKGROUND));
    if(bitmap == null) {
      VoiceControlForPlexApplication.getInstance().fetchNotificationBitmap(PlexMedia.IMAGE_KEY.WEAR_BACKGROUND, media, new Runnable() {
        @Override
        public void run() {
          Bitmap bitmap = VoiceControlForPlexApplication.getInstance().getCachedBitmap(media.getImageKey(PlexMedia.IMAGE_KEY.WEAR_BACKGROUND));
          Logger.d("Done fetching bitmap from cache, got: %s", bitmap);
          onFinished.onSuccess(bitmap);
        }
      });
    } else {
      Logger.d("Bitmap was already in cache: %s", bitmap);
      onFinished.onSuccess(bitmap);

    }
  }

  public static Asset createAssetFromBitmap(Bitmap bitmap) {
    final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
    return Asset.createFromBytes(byteStream.toByteArray());
  }

  public static void SetWearMediaTitles(DataMap dataMap, PlexMedia media) {
    if(media.isShow()) {
      dataMap.putString(WearConstants.MEDIA_TITLE, media.getTitle());
      dataMap.putString(WearConstants.MEDIA_SUBTITLE, media.getEpisodeTitle());
    } else if(media.isMovie()) {
      dataMap.putString(WearConstants.MEDIA_TITLE, media.title);
      dataMap.remove(WearConstants.MEDIA_SUBTITLE);
    } else if(media.isMusic()) {
      dataMap.putString(WearConstants.MEDIA_TITLE, media.grandparentTitle);
      dataMap.putString(WearConstants.MEDIA_SUBTITLE, media.title);
    }
  }

  public static String getUUID() {
    String uuid = VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.UUID, null);
    if(uuid == null) {
      uuid = UUID.randomUUID().toString();
      VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.UUID, uuid);
    }
    return uuid;
  }
}
