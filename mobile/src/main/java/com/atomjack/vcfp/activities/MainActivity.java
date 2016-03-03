package com.atomjack.vcfp.activities;

import android.Manifest;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.vending.billing.IabHelper;
import com.android.vending.billing.IabResult;
import com.android.vending.billing.Purchase;
import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.Preferences;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.UriDeserializer;
import com.atomjack.shared.UriSerializer;
import com.atomjack.shared.WearConstants;
import com.atomjack.vcfp.BuildConfig;
import com.atomjack.vcfp.CastPlayerManager;
import com.atomjack.vcfp.Feedback;
import com.atomjack.vcfp.FutureRunnable;
import com.atomjack.vcfp.NetworkMonitor;
import com.atomjack.vcfp.PlexSubscription;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.Utils;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.adapters.PlexListAdapter;
import com.atomjack.vcfp.fragments.CastPlayerFragment;
import com.atomjack.vcfp.fragments.MainFragment;
import com.atomjack.vcfp.fragments.PlayerFragment;
import com.atomjack.vcfp.fragments.PlexPlayerFragment;
import com.atomjack.vcfp.fragments.SetupFragment;
import com.atomjack.vcfp.interfaces.ActivityListener;
import com.atomjack.vcfp.interfaces.BitmapHandler;
import com.atomjack.vcfp.interfaces.PlexSubscriptionListener;
import com.atomjack.vcfp.interfaces.ScanHandler;
import com.atomjack.vcfp.model.Pin;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexDevice;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexUser;
import com.atomjack.vcfp.model.Stream;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpUserHandler;
import com.atomjack.vcfp.net.PlexPinResponseHandler;
import com.atomjack.vcfp.services.PlexScannerService;
import com.cubeactive.martin.inscription.WhatsNewDialog;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.wearable.DataMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.splunk.mint.Mint;

import org.honorato.multistatetogglebutton.MultiStateToggleButton;
import org.honorato.multistatetogglebutton.ToggleButton;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity
        implements VoiceControlForPlexApplication.NetworkChangeListener,
        ActivityListener,
        TextToSpeech.OnInitListener{

  public final static int RESULT_VOICE_FEEDBACK_SELECTED = 0;
  public final static int RESULT_TASKER_PROJECT_IMPORTED = 1;
  public final static int RESULT_SHORTCUT_CREATED = 2;

  public final static String ACTION_SHOW_NOW_PLAYING = "com.atomjack.vcfp.action_show_now_playing";

  public final static int SERVER_SCAN_INTERVAL = 1000*60*5; // scan for servers every 5 minutes

  private Handler handler;

  public final static String BUGSENSE_APIKEY = "879458d0";

  private ArrayList<String> availableVoices;
  private boolean settingErrorFeedback = false;
  private TextToSpeech tts;

  private DrawerLayout mDrawer;
  private Toolbar toolbar;
  private NavigationView navigationViewMain;
  private ActionBarDrawerToggle drawerToggle;
  private boolean mainNavigationItemsVisible = true;

  private String authToken;

  protected static final int REQUEST_WRITE_STORAGE = 112;

  // Whether or not we received device logs from a wear device. This will allow a timer to be run in case wear support has
  // been purchased, but no wear device is paired. When this happens, we'll go ahead and email just the mobile device's logs
  //
  private boolean receivedWearLogsResponse = false;

  // the currently selected server and client
  private PlexServer server;
  private PlexClient client;

  private TextView deviceSelectNoDevicesFound;
  private CheckBox deviceListResume;

  private FutureRunnable fetchPinTask;

  public Feedback feedback;

  MediaRouter mMediaRouter;
  MediaRouterCallback mMediaRouterCallback;
  MediaRouteSelector mMediaRouteSelector;

  protected PlexClient postChromecastPurchaseClient = null;
  protected Runnable postChromecastPurchaseAction = null;

  protected Gson gsonWrite = new GsonBuilder()
          .registerTypeAdapter(Uri.class, new UriSerializer())
          .create();
  protected Gson gsonRead = new GsonBuilder()
          .registerTypeAdapter(Uri.class, new UriDeserializer())
          .create();

  private boolean userIsInteracting;

  // First time setup
  private boolean doingFirstTimeSetup = false;
  // The next two booleans will be set to true when their respective scans are finished. This will ensure
  // that when whichever of the two finishes last, the screen will refresh and first time setup will be done.
  private boolean firstTimeSetupServerScanFinished = false;
  private boolean firstTimeSetupClientScanFinished = false;

  Preferences prefs;

  private AlertDialog alertDialog;

  private MenuItem castIconMenuItem;
  private boolean subscribed = false;
  private boolean subscribing = false;
  protected Dialog deviceSelectDialog = null;
  private ProgressBar serverListRefreshSpinner;
  private ImageView serverListRefreshButton;

  protected PlexSubscription plexSubscription;
  protected CastPlayerManager castPlayerManager;

  private PlayerFragment playerFragment;
  private MainFragment mainFragment;

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
  protected NetworkMonitor networkMonitor;

  LinearLayout navigationFooter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Logger.d("[MainActivity] onCreate");

//    B​aseCastManager.checkGooglePlayServices(this);

    // This will enable the UI to be updated (Wear Support hidden/Wear Options shown)
    // once inventory is queried via Google, if wear support has been purchased
    VoiceControlForPlexApplication.getInstance().setOnHasWearActivity(this);

    if(savedInstanceState != null) {
      client = savedInstanceState.getParcelable(com.atomjack.shared.Intent.EXTRA_CLIENT);
      playerFragment = (PlayerFragment)getSupportFragmentManager().getFragment(savedInstanceState, com.atomjack.shared.Intent.EXTRA_PLAYER_FRAGMENT);
      Logger.d("playerFragment: %s", playerFragment);
    } else {
      Logger.d("savedInstanceState is null");
    }

    prefs = VoiceControlForPlexApplication.getInstance().prefs;
    feedback = new Feedback(this);

    authToken = prefs.getString(Preferences.AUTHENTICATION_TOKEN);

    networkMonitor = new NetworkMonitor(this);
    VoiceControlForPlexApplication.getInstance().setNetworkChangeListener(this);

    currentNetworkState = NetworkState.getCurrentNetworkState(this);

    plexSubscription = VoiceControlForPlexApplication.getInstance().plexSubscription;
    castPlayerManager = VoiceControlForPlexApplication.getInstance().castPlayerManager;

    if(gsonRead.fromJson(prefs.get(Preferences.SUBSCRIBED_CLIENT, ""), PlexClient.class) != null) {
      if(prefs.get(Preferences.CRASHED, false)) {
        prefs.remove(Preferences.SUBSCRIBED_CLIENT);
      } else {
        client = gsonRead.fromJson(prefs.get(Preferences.SUBSCRIBED_CLIENT, ""), PlexClient.class);
        if (client.isCastClient) {
          if (!castPlayerManager.isSubscribed()) {
            castPlayerManager.subscribe(client);
          }
        } else if (!plexSubscription.isSubscribed()) {
          plexSubscription.subscribe(client);
        }
      }
      prefs.put(Preferences.CRASHED, false);
    }

    doingFirstTimeSetup = !prefs.get(Preferences.FIRST_TIME_SETUP_COMPLETED, false);
    // If auth token has already been set even though first time setup isn't complete, assume user
    // has upgraded
    if(authToken != null && doingFirstTimeSetup) {
      doingFirstTimeSetup = false;
      prefs.put(Preferences.FIRST_TIME_SETUP_COMPLETED, true);
    }

    // If plex email hasn't been saved, fetch it and refresh the navigation drawer when done. Previous versions
    // of the app were not saving the email, which is needed for the user icon.
    checkForMissingPlexEmail();

    setContentView(R.layout.main);

    if(!plexSubscription.isSubscribed() && !castPlayerManager.isSubscribed()) {
      Logger.d("Not subscribed: %s", plexSubscription.mClient);
      // In case the notification is still up due to a crash
      VoiceControlForPlexApplication.getInstance().cancelNotification();
    }

    init();
    if(!doingFirstTimeSetup)
      doAutomaticDeviceScan();
    if(plexSubscription.isSubscribed() || castPlayerManager.isSubscribed())
      setCastIconActive();
    else
      Logger.d("Not subscribed");
  }

  @Override
  public void onBackPressed() {
    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_MAIN);
    intent.addCategory(Intent.CATEGORY_HOME);
    startActivity(intent);
  }

  private PlexSubscriptionListener plexSubscriptionListener = new PlexSubscriptionListener() {
    @Override
    public void onSubscribed(final PlexClient client) {
      Logger.d("[MainActivity] onSubscribed");

      prefs.put(Preferences.SUBSCRIBED_CLIENT, gsonWrite.toJson(client));

      subscribing = false;
      try {
        setCastIconActive();
      } catch (Exception e) {
        e.printStackTrace();
      }
      feedback.m(String.format(getString(R.string.connected_to2), client.name));
    }

    @Override
    public void onTimeUpdate(PlayerState state, int seconds) {
      if(playerFragment != null) {
        playerFragment.setPosition(seconds);
        playerFragment.setState(state);
        handler.removeCallbacks(autoDisconnectPlayerTimer);
      } else
        Logger.d("Got time update of %d seconds, but for some reason playerFragment is null", seconds);

    }

    @Override
    public void onMediaChanged(PlexMedia media, PlayerState state) {
      Logger.d("[MainActivity] onMediaChanged: %s %s", media.getTitle(), state);
      playerFragment.mediaChanged(media);
      sendWearPlaybackChange(state, media);
    }

    @Override
    public void onPlayStarted(PlexMedia media, PlayerState state) {
      Logger.d("[MainActivity] onPlayStarted: %s", media.getTitle());
      int layout = getLayoutForMedia(media, state);

      if(layout != -1) {
        playerFragment.init(layout, client, media, false, plexSubscriptionListener);
        switchToFragment(playerFragment);
      }
      sendWearPlaybackChange(state, media);
    }

    @Override
    public void onStateChanged(PlexMedia media, PlayerState state) {
      Logger.d("[MainActivity] onStateChanged: %s", state);
      if(playerFragment != null && playerFragment.isVisible()) {
        if(state == PlayerState.STOPPED) {
          Logger.d("[MainActivity] onStopped");
          switchToFragment(getMainFragment());
        } else {
          playerFragment.setState(state);
        }
      } else {
        Logger.d("Got state change to %s, but for some reason playerFragment is null", state);
      }
      sendWearPlaybackChange(state, media);
    }

    @Override
    public void onSubscribeError(String message) {
      Logger.d("[MainActivity] onSubscribeError");
      setCastIconInactive();
      subscribing = false;
      feedback.e(String.format(getString(R.string.cast_connect_error), client.name));
    }

    @Override
    public void onUnsubscribed() {
      Logger.d("[MainActivity] unsubscribed");
      setCastIconInactive();
      prefs.remove(Preferences.SUBSCRIBED_CLIENT);
      switchToFragment(getMainFragment());
      if(VoiceControlForPlexApplication.getInstance().hasWear()) {
        new SendToDataLayerThread(WearConstants.DISCONNECTED, MainActivity.this).start();
      }
      feedback.m(R.string.disconnected);
    }
  };

  private void switchToFragment(Fragment fragment) {
    getSupportFragmentManager().beginTransaction().replace(R.id.flContent, fragment).commitAllowingStateLoss();
  }

  private void init() {
    handler = new Handler();

    if(BuildConfig.USE_BUGSENSE) {
      Mint.disableNetworkMonitoring();
      Mint.initAndStartSession(getApplicationContext(), BUGSENSE_APIKEY);
    }

    // Set a Toolbar to replace the ActionBar.
    toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);

    Fragment fragment;
    if (!doingFirstTimeSetup) {

      mMediaRouter = MediaRouter.getInstance(getApplicationContext());
      mMediaRouteSelector = new MediaRouteSelector.Builder()
              .addControlCategory(CastMediaControlIntent.categoryForCast(BuildConfig.CHROMECAST_APP_ID))
              .build();
      mMediaRouterCallback = new MediaRouterCallback();
      mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);

      server = gsonRead.fromJson(prefs.get(Preferences.SERVER, ""), PlexServer.class);
      if (server == null)
        server = PlexServer.getScanAllServer();

      client = gsonRead.fromJson(prefs.get(Preferences.CLIENT, ""), PlexClient.class);

      setupNavigationDrawer();

      Logger.d("Intent action: %s", getIntent().getAction());
      if(getIntent().getAction() != null && getIntent().getAction().equals(ACTION_SHOW_NOW_PLAYING)) {
        handleShowNowPlayingIntent(getIntent());
      } else {

        Logger.d("Loading main fragment");

        fragment = playerFragment != null ? playerFragment : getMainFragment();

        // Only show the what's new dialog if this is not the first time the app is run
        showWhatsNewDialog(false);
        switchToFragment(fragment);
      }
    } else {
      fragment = new SetupFragment();
      switchToFragment(fragment);
    }
  }

  private void showWhatsNewDialog(boolean force) {
    WhatsNewDialog whatsNewDialog = new WhatsNewDialog(this);
    whatsNewDialog.setStyle(String.format("body { background-color: %s; color: #ffffff; }" +
            "h1 { margin-left: 0px; font-size: 12pt; }"
            + "li { margin-left: 0px; font-size: 9pt; }"
            + "ul { padding-left: 30px; }"
            + ".summary { font-size: 9pt; color: #606060; display: block; clear: left; }"
            + ".date { font-size: 9pt; color: #606060;  display: block; }", String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(this, R.color.settings_popup_background)))));
    View customView = LayoutInflater.from(this).inflate(R.layout.popup_whatsnew, null, false);
    whatsNewDialog.setCustomView(customView);
    if(force)
      whatsNewDialog.forceShow();
    else
      whatsNewDialog.show();
  }

  @Override
  protected void onPause() {
    super.onPause();
    Logger.d("[MainActivity] onPause");
    VoiceControlForPlexApplication.applicationPaused();
    if (isFinishing()) {
      mMediaRouter.removeCallback(mMediaRouterCallback);
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    Logger.d("[MainActivity] onStop");
    if(prefs.get(Preferences.SERVER_SCAN_FINISHED, true) == false) {
      Intent scannerIntent = new Intent(MainActivity.this, PlexScannerService.class);
      scannerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      scannerIntent.setAction(PlexScannerService.CANCEL);
      startService(scannerIntent);
    }
    handler.removeCallbacks(refreshServers);
    handler.removeCallbacks(refreshClients);
    plexSubscription.removeListener(plexSubscriptionListener);

    // TODO: Remove castplayermanager listener
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    Logger.d("[MainActivity] onRestart");
    VoiceControlForPlexApplication.getInstance().refreshInAppInventory();
  }

  private Runnable autoDisconnectPlayerTimer = new Runnable() {
    @Override
    public void run() {
      Logger.d("Auto disconnecting player");
      if(playerFragment.isVisible()) {
        VoiceControlForPlexApplication.getInstance().cancelNotification();
        switchToFragment(mainFragment);
      }
    }
  };

  @Override
  protected void onResume() {
    super.onResume();
    Logger.d("[MainActivity] onResume, interacting: %s", userIsInteracting);
    VoiceControlForPlexApplication.applicationResumed();

    plexSubscription.setListener(plexSubscriptionListener);
    castPlayerManager.setListener(plexSubscriptionListener);
    if(!doingFirstTimeSetup) {
      mDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    } else
      mDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
  }

  private void doAutomaticDeviceScan() {
    if(BuildConfig.AUTO_REFRESH_DEVICES) {
      Logger.d("Doing automatic device scan");
      // Kick off a scan for servers, if it's been more than five minutes since the last one.
      // We'll do this every five, to keep the list up to date. Also, if the last server scan didn't
      // finish, kick off another one right now instead (another scan in 5 minutes will be queued up when that one finishes).
      int s =  VoiceControlForPlexApplication.getInstance().getSecondsSinceLastServerScan();
      Logger.d("It's been %d seconds since last scan, last scan finished: %s", s, prefs.get(Preferences.SERVER_SCAN_FINISHED, true));
      if (s >= (SERVER_SCAN_INTERVAL / 1000) || prefs.get(Preferences.SERVER_SCAN_FINISHED, true) == false) {
        Logger.d("It's been more than 5 minutes since last scan, so scanning now.");
        refreshServers.run();
        refreshClients.run();
      } else {
        int d = (SERVER_SCAN_INTERVAL - (s * 1000));
        Logger.d("It's been less than 5 minutes since the last server scan, so doing another in %d ms", d);
        handler.removeCallbacks(refreshServers);
        handler.removeCallbacks(refreshClients);
        handler.postDelayed(refreshServers, d);
        handler.postDelayed(refreshClients, d);
      }
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Logger.d("onActivityResult: %d, %d", requestCode, resultCode);
    // Pass on the activity result to the helper for handling
    if (VoiceControlForPlexApplication.getInstance().getIabHelper() == null || !VoiceControlForPlexApplication.getInstance().getIabHelper().handleActivityResult(requestCode, resultCode, data)) {
      if (requestCode == RESULT_VOICE_FEEDBACK_SELECTED) {
        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
          // success, create the TTS instance
          availableVoices = data.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
          Collections.sort(availableVoices);
          // Need this or else voice selection won't show up:
          tts = new TextToSpeech(this, this);
        } else {
          // missing data, install it
          Intent installIntent = new Intent();
          installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
          startActivity(installIntent);
        }
      } else if (requestCode == RESULT_SHORTCUT_CREATED) {
        if (resultCode == RESULT_OK) {
          data.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
          sendBroadcast(data);

          feedback.m(getString(R.string.shortcut_created));
        }
      }
    } else {
      Logger.d("onActivityResult handled by IABUtil.");
    }
  }

  public void mainLoadingBypassLogin(View v) {
    showFindingPlexClientsAndServers();
    refreshServers.run();
    refreshClients.run();
  }

  public void showLogin(View v) { showLogin(); }
  public void showLogin(MenuItem item) {
    showLogin();
  }

  public void showLogin() {
    PlexHttpClient.getPinCode(new PlexPinResponseHandler() {
      @Override
      public void onSuccess(Pin pin) {
        showPin(pin);
      }

      @Override
      public void onFailure(Throwable error) {
        error.printStackTrace();
        feedback.e(R.string.login_error);
      }
    });
  }

  private void showPin(final Pin pin) {
    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
    View view = getLayoutInflater().inflate(R.layout.popup_plex_pin, null);
    alertDialogBuilder.setView(view);
    // create and show an alert dialog
    final AlertDialog pinAlert = alertDialogBuilder.create();
    pinAlert.show();

    TextView popupPlexPinMessage = (TextView)view.findViewById(R.id.popupPlexPinMessage);
    popupPlexPinMessage.setText(String.format(getString(R.string.pin_message), pin.code));
    Button popupPlexPinManualButton = (Button)view.findViewById(R.id.popupPlexPinManualButton);
    popupPlexPinManualButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        pinAlert.dismiss();
        fetchPinTask.getFuture().cancel(false);
        showManualLogin();
      }
    });
    Button popupPlexPinCancelButton = (Button)view.findViewById(R.id.popupPlexPinCancelButton);
    popupPlexPinCancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        pinAlert.cancel();
        fetchPinTask.getFuture().cancel(false);
      }
    });

    // Now set up a task to hit the below url (based on the "id" field returned in the above http POST)
    // every second. Once the user has entered the code on the plex website, the xml returned from the
    // below http GET will contain their authentication token. Once that is retrieved, save it, switch
    // the showLogin/logout buttons in the menu, and cancel the dialog.
    final Context context = MainActivity.this;
    fetchPinTask = new FutureRunnable() {
      @Override
      public void run() {
        PlexHttpClient.fetchPin(pin.id, new PlexPinResponseHandler() {
          @Override
          public void onSuccess(Pin pin) {
            if(pin.authToken != null) {
              authToken = pin.authToken;
              prefs.put(Preferences.AUTHENTICATION_TOKEN, authToken);
              PlexHttpClient.signin(authToken, new PlexHttpUserHandler() {
                @Override
                public void onSuccess(PlexUser user) {
                  prefs.put(Preferences.PLEX_USERNAME, user.username);
                  prefs.put(Preferences.PLEX_EMAIL, user.email);
                  if(doingFirstTimeSetup) {
                    showFindingPlexClientsAndServers();
                    refreshServers.run();
                    refreshClients.run();
                  } else {
                    setupNavigationDrawer();
                    refreshServers(null);
                  }
                }

                @Override
                public void onFailure(int statusCode) {
                  // TODO: Handle failure
                }
              });
              pinAlert.cancel();
              Handler mainHandler = new Handler(context.getMainLooper());
              mainHandler.post(new Runnable() {
                @Override
                public void run() {
                  feedback.m(R.string.logged_in);
                }
              });
              // We got the auth token, so cancel this task
              getFuture().cancel(false);
            }
          }

          @Override
          public void onFailure(Throwable error) {
            error.printStackTrace();
          }
        });
      }
    };
    // Set up the schedule service and let fetchPinTask know of the Future object, so the task can cancel
    // itself once the authentication token is retrieved.
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    Future<?> future = executor.scheduleAtFixedRate(fetchPinTask, 0, 1000, TimeUnit.MILLISECONDS);
    fetchPinTask.setFuture(future);
  }

  // This gets called in first time setup, after the user has logged in
  private void showFindingPlexClientsAndServers() {
    LayoutInflater inflater = getLayoutInflater();
    View layout = inflater.inflate(R.layout.search_popup, null);

    alertDialog = new AlertDialog.Builder(this)
            .setCancelable(false)
            .setView(layout)
            .create();
    alertDialog.show();
  }

  private void showManualLogin() {
    LayoutInflater layoutInflater = LayoutInflater.from(MainActivity.this);
    View view = layoutInflater.inflate(R.layout.login, null);
    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
    builder.setView(view);
    final EditText usernameInput = (EditText) view.findViewById(R.id.usernameInput);
    final EditText passwordInput = (EditText) view.findViewById(R.id.passwordInput);

    final AlertDialog alertD = builder.create();

    Button popupManualLoginPinButton = (Button)view.findViewById(R.id.popupManualLoginPinButton);
    popupManualLoginPinButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        alertD.dismiss();
        showLogin();
      }
    });
    Button popupManualLoginCancelButton = (Button)view.findViewById(R.id.popupManualLoginCancelButton);
    popupManualLoginCancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        alertD.cancel();
      }
    });
    Button popupManualLoginOKButton = (Button)view.findViewById(R.id.popupManualLoginOKButton);
    popupManualLoginOKButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        PlexHttpClient.signin(usernameInput.getText().toString(), passwordInput.getText().toString(), new PlexHttpUserHandler() {
          @Override
          public void onSuccess(PlexUser user) {
            prefs.put(Preferences.AUTHENTICATION_TOKEN, user.authenticationToken);
            authToken = user.authenticationToken;
            prefs.put(Preferences.PLEX_USERNAME, user.username);
            prefs.put(Preferences.PLEX_EMAIL, user.email);
            feedback.m(R.string.logged_in);
            if(doingFirstTimeSetup) {
              showFindingPlexClientsAndServers();
              refreshServers.run();
              refreshClients.run();
            } else {
              setupNavigationDrawer();
              refreshServers(null);
            }
            alertD.cancel();
          }

          @Override
          public void onFailure(int statusCode) {
            Logger.e("Failure logging in");
            String err = getString(R.string.login_error);
            if(statusCode == 401) {
              err = getString(R.string.login_incorrect);
            }
            feedback.e(err);
            alertD.cancel();
          }
        });
      }
    });
    builder
            .setCancelable(true);
    alertD.show();
  }

  private void setServer(PlexServer s) {
    server = s;
    saveSettings();
    if(getMainFragment().isVisible())
      getMainFragment().setServer(s);
    if(!doingFirstTimeSetup)
      refreshNavServers();
  }

  public void logout(MenuItem item) {
    Logger.d("logging out");

    prefs.remove(Preferences.AUTHENTICATION_TOKEN);
    prefs.remove(Preferences.PLEX_USERNAME);
    authToken = null;

    if(!server.local) {
      setServer(PlexServer.getScanAllServer());
    }

    // Remove any non-local servers from our list
    for(PlexServer s : VoiceControlForPlexApplication.servers.values()) {
      if(!s.local)
        VoiceControlForPlexApplication.servers.remove(s.name);
    }

    refreshNavServers();

    prefs.put(Preferences.SAVED_SERVERS, gsonWrite.toJson(VoiceControlForPlexApplication.servers));
    saveSettings();

    // Refresh the navigation drawer
    setupNavigationDrawer();

    feedback.m(R.string.logged_out);
  }

  private void setupNavigationDrawer() {
    mDrawer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    drawerToggle = new ActionBarDrawerToggle(this, mDrawer, toolbar, R.string.drawer_open, R.string.drawer_close) {
      @Override
      public void onDrawerSlide(View drawerView, float slideOffset) {
//        super.onDrawerSlide(drawerView, 0);
        // Do nothing. This will ensure the hamburger icon is restored
      }

      @Override
      public void onDrawerClosed(View drawerView) {
        super.onDrawerClosed(drawerView);
        setNavGroup(R.menu.nav_items_main);
      }


    };
    mDrawer.setDrawerListener(drawerToggle);
    // Find our drawer view
    if(navigationViewMain == null)
      navigationViewMain = (NavigationView) findViewById(R.id.navigationViewMain);

    // Footer view
    navigationFooter = (LinearLayout) findViewById(R.id.navigationViewFooter);

    final LinearLayout navigationFooterHelpButton = (LinearLayout)navigationFooter.findViewById(R.id.navigationFooterHelpButton);
    navigationFooterHelpButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        navigationFooterHelpButton.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.primary_600));
        handler.postDelayed(new Runnable() {
          @Override
          public void run() {
            navigationFooterHelpButton.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.navigation_drawer_background));
          }
        }, 200);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        View view = getLayoutInflater().inflate(R.layout.help_dialog, null);
        builder.setView(view);
        final AlertDialog usageDialog = builder.create();
        Button button = (Button)view.findViewById(R.id.helpCloseButton);
        button.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            usageDialog.dismiss();
          }
        });
        usageDialog.show();
      }
    });

    final LinearLayout navigationFooterSettingsButton = (LinearLayout)navigationFooter.findViewById(R.id.navigationFooterSettingsButton);
    navigationFooterSettingsButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        navigationFooterSettingsButton.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.primary_600));
        handler.postDelayed(new Runnable() {
          @Override
          public void run() {
            navigationFooterSettingsButton.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.navigation_drawer_background));
            setNavGroup(R.menu.nav_items_settings);
          }
        }, 200);
      }
    });

    if(navigationViewMain.getHeaderView(0) != null)
      navigationViewMain.removeHeaderView(navigationViewMain.getHeaderView(0));

    refreshNavServers();

    if(authToken != null) {
      navigationViewMain.inflateHeaderView(R.layout.nav_header_logged_in);
      if(prefs.getString(Preferences.PLEX_EMAIL) != null) {
        setUserThumb();
        Logger.d("Username = %s", prefs.getString(Preferences.PLEX_USERNAME));
        final View navHeader = navigationViewMain.getHeaderView(0);

        serverListRefreshSpinner = (ProgressBar)navHeader.findViewById(R.id.serverListRefreshSpinner);
        serverListRefreshButton = (ImageView)navHeader.findViewById(R.id.serverListRefreshButton);

        TextView navHeaderUsername = (TextView)navHeader.findViewById(R.id.navHeaderUsername);
        navHeaderUsername.setText(prefs.getString(Preferences.PLEX_USERNAME));

        // When the user clicks on their username, show the logout button
        final LinearLayout navHeaderUserRow = (LinearLayout)navHeader.findViewById(R.id.navHeaderUserRow);
        navHeaderUserRow.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            int flip = R.animator.flip_down;
            if(mainNavigationItemsVisible) {
              setNavGroup(R.menu.nav_items_logged_in);
              mainNavigationItemsVisible = false;
              flip = R.animator.flip_up;
            } else {
              setNavGroup(R.menu.nav_items_main);
              mainNavigationItemsVisible = true;
            }
            // Flip the arrow that is to the right of the username
            ImageView image = (ImageView)navHeaderUserRow.findViewById(R.id.navHeaderUserArrow);
            AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(MainActivity.this, flip);
            set.setTarget(image);
            set.start();
          }
        });
      }
    } else {
      navigationViewMain.inflateHeaderView(R.layout.nav_header_logged_out);
      final View navHeader = navigationViewMain.getHeaderView(0);
      serverListRefreshSpinner = (ProgressBar)navHeader.findViewById(R.id.serverListRefreshSpinner);
      serverListRefreshButton = (ImageView)navHeader.findViewById(R.id.serverListRefreshButton);
      final LinearLayout navHeaderUserRow = (LinearLayout)navHeader.findViewById(R.id.navHeaderUserRow);
      navHeaderUserRow.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          showLogin();
        }
      });
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Logger.d("[MainActivity] onNewIntent: %s", intent.getAction());

    if(intent.getAction() != null) {
      if (intent.getAction().equals(PlexScannerService.ACTION_SERVER_SCAN_FINISHED)) {
        HashMap<String, PlexServer> s = (HashMap<String, PlexServer>) intent.getSerializableExtra(com.atomjack.shared.Intent.EXTRA_SERVERS);
        Logger.d("[MainActivity] finished scanning for servers, have %d servers", s.size());
        // Save the fact that we've finished this server scan
        prefs.put(Preferences.SERVER_SCAN_FINISHED, true);
        VoiceControlForPlexApplication.servers = new ConcurrentHashMap<>(s);
        // If the currently selected server is not in the list of servers we now have, set the current server to scan all
        if(server == null || !VoiceControlForPlexApplication.servers.containsKey(server.name)) {
          setServer(PlexServer.getScanAllServer());
        }
        prefs.put(Preferences.SAVED_SERVERS, gsonWrite.toJson(VoiceControlForPlexApplication.servers));
        Logger.d("doing first time setup: %s, client scan finished: %s", doingFirstTimeSetup, firstTimeSetupClientScanFinished);
        if(doingFirstTimeSetup) {
          firstTimeSetupServerScanFinished = true;
          if(firstTimeSetupClientScanFinished)
            onFirstTimeScanFinished();
        } else {
          // Refresh the list of servers in the navigation drawer
          refreshNavServers();
        }
        if(onServerRefreshFinished != null)
          onServerRefreshFinished.run();
      } else if(intent.getAction().equals(PlexScannerService.ACTION_CLIENT_SCAN_FINISHED)) {
        VoiceControlForPlexApplication.clients = new HashMap<>();
        List<PlexClient> c = (ArrayList<PlexClient>)intent.getSerializableExtra(com.atomjack.shared.Intent.EXTRA_CLIENTS);
        if(c != null) {
          Logger.d("Got %d clients", c.size());
          for (PlexClient client : c) {
            if (!VoiceControlForPlexApplication.clients.containsKey(client.name)) {
              VoiceControlForPlexApplication.clients.put(client.name, client);
              Logger.d("Saved %s", client.name);
            }
          }
          prefs.put(Preferences.SAVED_CLIENTS, gsonWrite.toJson(VoiceControlForPlexApplication.clients));
        }
        if (doingFirstTimeSetup) {
          firstTimeSetupClientScanFinished = true;
          if(firstTimeSetupServerScanFinished)
            onFirstTimeScanFinished();
        }
        if(VoiceControlForPlexApplication.getAllClients().size() == 0) {
          deviceSelectNoDevicesFound.setVisibility(View.VISIBLE);
          deviceListResume.setVisibility(View.GONE);
        } else {
          deviceSelectNoDevicesFound.setVisibility(View.GONE);
          deviceListResume.setVisibility(View.VISIBLE);
        }
        if(onClientRefreshFinished != null) {
          onClientRefreshFinished.run();
        }


      } else if(intent.getAction().equals(ACTION_SHOW_NOW_PLAYING)) {
        handleShowNowPlayingIntent(intent);
      } else if(intent.getAction() != null && intent.getAction().equals(com.atomjack.shared.Intent.SHOW_WEAR_PURCHASE)) {
        // An Android Wear device was successfully pinged, so show popup alerting the
        // user that they can purchase wear support, but only if we've never shown the popup before.
        if(VoiceControlForPlexApplication.getInstance().hasWear()) {
          hidePurchaseWearMenuItem();
        } else {
          if (prefs.get(Preferences.HAS_SHOWN_WEAR_PURCHASE_POPUP, false) == false)
            showWearPurchase();
        }
      } else if(intent.getAction() != null && intent.getAction().equals(com.atomjack.shared.Intent.SHOW_WEAR_PURCHASE_REQUIRED)) {
        showWearPurchaseRequired();
      } else if(intent.getAction() != null && intent.getAction().equals(WearConstants.GET_DEVICE_LOGS)) {
        String wearLog = intent.getStringExtra(WearConstants.LOG_CONTENTS);
        receivedWearLogsResponse = true;
        emailDeviceLogs(wearLog);
      } else if(intent.getAction().equals(com.atomjack.shared.Intent.GET_PLAYING_MEDIA)) {
        PlexMedia media = null;
        if(plexSubscription.isSubscribed())
          media = plexSubscription.getNowPlayingMedia();
        else if(castPlayerManager.isSubscribed())
          media = castPlayerManager.getNowPlayingMedia();
        if(media != null) {
          // Send information on the currently playing media to the wear device
          DataMap data = new DataMap();
          data.putString(WearConstants.MEDIA_TITLE, media.title);
          data.putString(WearConstants.IMAGE, media.art);
          new SendToDataLayerThread(WearConstants.GET_PLAYING_MEDIA, data, this).start();
        }
      }
    }
  }

  // This is called after first time setup client & server scan is done.
  private void onFirstTimeScanFinished() {
    Logger.d("first time scan finished");
    doingFirstTimeSetup = false;
    prefs.put(Preferences.FIRST_TIME_SETUP_COMPLETED, true);
    if(alertDialog != null)
      alertDialog.dismiss();
    init();
    drawerToggle.syncState();
    doAutomaticDeviceScan();
  }

  private void handleShowNowPlayingIntent(Intent intent) {
    client = intent.getParcelableExtra(com.atomjack.shared.Intent.EXTRA_CLIENT);
    PlexMedia media = intent.getParcelableExtra(com.atomjack.shared.Intent.EXTRA_MEDIA);
    Logger.d("[MainActivity] show now playing: %s", media.getTitle());
    boolean fromWear = intent.getBooleanExtra(WearConstants.FROM_WEAR, false);
    PlayerState state;
    if(client.isCastClient) {
      state = castPlayerManager.getCurrentState();
    } else {
      state = plexSubscription.getCurrentState();
      plexSubscription.subscribe(client);
    }
    int layout = getLayoutForMedia(media, state);
    Logger.d("Layout: %d", layout);
    if(layout != -1) {
      playerFragment.init(layout, client, media, fromWear, plexSubscriptionListener);
      if(playerFragment.isVisible())
        playerFragment.mediaChanged(media);
      else
        switchToFragment(playerFragment);
      int seconds = intent.getBooleanExtra(com.atomjack.shared.Intent.EXTRA_STARTING_PLAYBACK, false) ? 10 : 3;
      Logger.d("Setting auto disconnect for %d seconds", seconds);
      handler.postDelayed(autoDisconnectPlayerTimer, seconds*1000);
    }
  }

  private Runnable refreshServers = new Runnable() {
    @Override
    public void run() {
      // First, save the fact that we have started but not yet finished this server scan. On startup, we'll check for this and if it hasn't finished, kick off
      // a new scan right away.
      Logger.d("Refreshing servers");
      prefs.put(Preferences.SERVER_SCAN_FINISHED, false);
      Intent scannerIntent = new Intent(MainActivity.this, PlexScannerService.class);
      scannerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      scannerIntent.putExtra(PlexScannerService.CLASS, MainActivity.class);
      scannerIntent.setAction(PlexScannerService.ACTION_SCAN_SERVERS);
      startService(scannerIntent);
      prefs.put(Preferences.LAST_SERVER_SCAN, new Date().getTime());
      handler.postDelayed(refreshServers, SERVER_SCAN_INTERVAL);
    }
  };

  private Runnable refreshClients = new Runnable() {
    @Override
    public void run() {
      Logger.d("Refreshing clients");
      Intent scannerIntent = new Intent(MainActivity.this, PlexScannerService.class);
      scannerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      scannerIntent.putExtra(PlexScannerService.CLASS, MainActivity.class);
      scannerIntent.putExtra(com.atomjack.shared.Intent.EXTRA_CONNECT_TO_CLIENT, false);
      scannerIntent.setAction(PlexScannerService.ACTION_SCAN_CLIENTS);
      startService(scannerIntent);
      handler.postDelayed(refreshClients, SERVER_SCAN_INTERVAL);
    }
  };

  // This needs to be called every time we update the servers list, so the spinner updates and sets the selection to the currently selected server
  private void refreshNavServers() {
    final HashMap<Integer, PlexServer> menuItemServerMap = new HashMap<>();

    MenuItem.OnMenuItemClickListener serverItemClickListener = new MenuItem.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        PlexServer s = menuItemServerMap.get(item.getItemId());
        setServer(s);
        return true;
      }
    };


    Menu menu = navigationViewMain.getMenu();
    menu.clear();
    PlexServer scanAllServer = PlexServer.getScanAllServer();
    MenuItem scanAllItem = menu.add(Menu.NONE, 0, 0, scanAllServer.name);
    scanAllItem.setCheckable(true);
    scanAllItem.setChecked(server.isScanAllServer);
    scanAllItem.setOnMenuItemClickListener(serverItemClickListener);
    scanAllItem.setIcon(R.drawable.menu_server);

    menuItemServerMap.put(0, scanAllServer);
    int id = 1;


    for(PlexServer thisServer : VoiceControlForPlexApplication.servers.values()) {
      MenuItem item = menu.add(Menu.NONE, id, id, thisServer.owned ? thisServer.name : thisServer.sourceTitle);
      item.setIcon(R.drawable.menu_server);
      item.setCheckable(true);
      item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
      LinearLayout layout = (LinearLayout)getLayoutInflater().inflate(thisServer.owned ? R.layout.nav_server_list_sections : R.layout.nav_server_list_sections_unowned, null);

      int numSections = thisServer.movieSections.size() + thisServer.tvSections.size() + thisServer.musicSections.size();
      TextView serverExtra = (TextView)layout.findViewById(R.id.serverListSections);
      serverExtra.setText(String.format("(%d %s)", numSections, getString(R.string.sections)));
      if (!thisServer.owned) {
        TextView serverExtraName = (TextView)layout.findViewById(R.id.serverListName);
        serverExtraName.setText(thisServer.name);
      }
      item.setActionView(layout);
      menuItemServerMap.put(id, thisServer);
      item.setChecked(server != null && server.machineIdentifier != null && server.machineIdentifier.equals(thisServer.machineIdentifier));
      item.setOnMenuItemClickListener(serverItemClickListener);
      id++;
    }
  }

  public void navMenuSettingsBack(MenuItem item) {
    setNavGroup(R.menu.nav_items_main);
  }

  private void setNavGroup(int group) {
    navigationViewMain.getMenu().clear();
    navigationViewMain.inflateMenu(group);
    Menu menu = navigationViewMain.getMenu();
    LinearLayout navHeaderPlexServersTitle = (LinearLayout)navigationViewMain.findViewById(R.id.navHeaderPlexServersTitle);
    if(group == R.menu.nav_items_main) {
      navHeaderPlexServersTitle.setVisibility(View.VISIBLE);
      navigationViewMain.setItemBackground(ContextCompat.getDrawable(this, R.drawable.nav_drawer_server_item));
      navigationFooter.setVisibility(View.VISIBLE);
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          refreshNavServers();
        }
      }, 1);
    } else {
      navHeaderPlexServersTitle.setVisibility(View.GONE);
      if(group == R.menu.nav_items_settings) {
        navigationViewMain.setItemBackground(ContextCompat.getDrawable(this, R.drawable.nav_drawer_item));
        navigationFooter.setVisibility(View.GONE);
        if(VoiceControlForPlexApplication.getInstance().hasWear())
          hidePurchaseWearMenuItem();

        if(VoiceControlForPlexApplication.getInstance().hasChromecast()) {
          MenuItem chromecastOptionsItem = menu.findItem(R.id.menu_chromecast_video);
          chromecastOptionsItem.setVisible(true);
        }

        if (!hasValidAutoVoice() && !hasValidUtter()) {
          menu.findItem(R.id.menu_tasker_import).setVisible(false);
          if (!hasValidTasker()) {
            menu.findItem(R.id.menu_install_tasker).setVisible(true);
          }
          if (!hasValidUtter()) {
            menu.findItem(R.id.menu_install_utter).setVisible(true);
          }
          if (!hasValidAutoVoice()) {
            menu.findItem(R.id.menu_install_autovoice).setVisible(true);
          }
        }
      }
    }
  }

  public void refreshServers(View v) {
    serverListRefreshButton.setVisibility(View.GONE);
    serverListRefreshSpinner.setVisibility(View.VISIBLE);
    onServerRefreshFinished = new Runnable() {
      @Override
      public void run() {
        serverListRefreshButton.setVisibility(View.VISIBLE);
        serverListRefreshSpinner.setVisibility(View.GONE);
        refreshNavServers();
        onServerRefreshFinished = null;
      }
    };

    refreshServers.run();
  }

  private void setUserThumb() {
    setUserThumb(false);
  }

  private void setUserThumb(boolean skipThumb) {
    final Bitmap bitmap = VoiceControlForPlexApplication.getInstance().getCachedBitmap(VoiceControlForPlexApplication.getInstance().getUserThumbKey());
    if(bitmap == null && !skipThumb) {
      fetchUserThumb();
    } else {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          View navHeader = navigationViewMain.getHeaderView(0);
          ImageView imageView = (ImageView) navHeader.findViewById(R.id.navHeaderUserIcon);
          if(bitmap == null)
            imageView.setImageResource(R.drawable.nav_default_user);
          else
            imageView.setImageBitmap(bitmap);
        }
      });
    }
  }

  private void fetchUserThumb() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        try {
          String url = String.format("http://www.gravatar.com/avatar/%s?s=60&d=404", Utils.md5(prefs.getString(Preferences.PLEX_EMAIL)));
          Logger.d("url: %s", url);
          byte[] imageData = PlexHttpClient.getSyncBytes(url);
          if(imageData != null) {
            Logger.d("got %d bytes", imageData.length);
            InputStream is = new ByteArrayInputStream(imageData);
            is.reset();
            VoiceControlForPlexApplication.getInstance().mSimpleDiskCache.put(VoiceControlForPlexApplication.getInstance().getUserThumbKey(), is);
          }
          setUserThumb(true);
        }
        catch(SocketTimeoutException e) {
          Logger.d("Couldn't get user thumb.");
        }
        catch(Exception ex) {
          ex.printStackTrace();
        }
        return null;
      }


    }.execute();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    if(!doingFirstTimeSetup) {
      getMenuInflater().inflate(R.menu.toolbar_cast, menu);
      castIconMenuItem = menu.findItem(R.id.action_cast);
      if (plexSubscription.isSubscribed() || castPlayerManager.isSubscribed()) {
        setCastIconActive();
      }
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
//    if (drawerToggle.onOptionsItemSelected(item)) {
//      return true;
//    }
    // The action bar home/up action should open or close the drawer.
    switch (item.getItemId()) {
      case android.R.id.home:
        mDrawer.openDrawer(GravityCompat.START);
        return true;
      case R.id.action_cast:
        castIconClick(onClientChosen);
        return true;
    }

    return super.onOptionsItemSelected(item);
  }

  private void animateCastIcon() {
    castIconMenuItem.setIcon(R.drawable.mr_ic_media_route_connecting_holo_dark);
    AnimationDrawable ad = (AnimationDrawable) castIconMenuItem.getIcon();
    ad.start();
  }

  // This is the default action that will be taken when a user subscribes to a client via the UI.
  private ScanHandler onClientChosen = new ScanHandler() {
    @Override
    public void onDeviceSelected(PlexDevice device, boolean resume) {
      if(device != null) {
        subscribing = true;
        PlexClient clientSelected = (PlexClient)device;
        setClient(clientSelected);

        // Start animating the action bar icon
        animateCastIcon();

        if (clientSelected.isCastClient) {
          if(VoiceControlForPlexApplication.getInstance().hasChromecast()) {
            client = clientSelected;
            Logger.d("[MainActivity] subscribing to %s", client.name);
            castPlayerManager.subscribe(client);
          } else {
            subscribing = false;
            setCastIconInactive();
            showChromecastPurchase(clientSelected, new Runnable() {
              @Override
              public void run() {
                animateCastIcon();
                subscribing = true;
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

  protected void setClient(PlexClient _client) {
    Logger.d("[MainActivity] setClient");
    client = _client;
    prefs.put(Preferences.CLIENT, gsonWrite.toJson(_client));
    if(mainFragment.isVisible())
      mainFragment.setClient(_client);
  }

  private boolean isSubscribed() {
    return plexSubscription.isSubscribed() || castPlayerManager.isSubscribed();
  }

  private Runnable onClientRefreshFinished = null;
  private Runnable onServerRefreshFinished = null;

  private void castIconClick(final ScanHandler onFinish) {
    if(!isSubscribed() && !subscribing) {

      final PlexListAdapter adapter = new PlexListAdapter(this, PlexListAdapter.TYPE_CLIENT);
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      LayoutInflater inflater = getLayoutInflater();
      final View layout = inflater.inflate(R.layout.device_select, null);
      final TextView headerView = (TextView)layout.findViewById(R.id.deviceListHeader);
      headerView.setText(R.string.select_plex_client);
      final ImageButton button = (ImageButton)layout.findViewById(R.id.deviceListRefreshButton);
      final ProgressBar spinnerImage = (ProgressBar) layout.findViewById(R.id.deviceListRefreshSpinner);

      button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          onClientRefreshFinished = new Runnable() {
            @Override
            public void run() {
              Logger.d("Changing buttons");
              button.setVisibility(View.VISIBLE);
              spinnerImage.setVisibility(View.GONE);
              Logger.d("Setting %d clients", VoiceControlForPlexApplication.getAllClients().size());
              adapter.setClients(VoiceControlForPlexApplication.getAllClients());

              adapter.notifyDataSetChanged();
              onClientRefreshFinished = null;
            }
          };
          Logger.d("Refreshing");
          button.setVisibility(View.GONE);
          spinnerImage.setVisibility(View.VISIBLE);
          handler.removeCallbacks(refreshClients);
          refreshClients.run();

        }
      });

      deviceListResume = (CheckBox) layout.findViewById(R.id.deviceListResume);
      deviceListResume.setVisibility(View.VISIBLE);
      deviceListResume.setChecked(prefs.get(Preferences.RESUME, false));
      deviceListResume.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          prefs.put(Preferences.RESUME, ((CheckBox) v).isChecked());
        }
      });
      deviceSelectNoDevicesFound = (TextView)layout.findViewById(R.id.deviceSelectNoDevicesFound);


      if(VoiceControlForPlexApplication.getAllClients().size() == 0) {
        deviceSelectNoDevicesFound.setVisibility(View.VISIBLE);
        deviceListResume.setVisibility(View.GONE);
      } else {
        deviceSelectNoDevicesFound.setVisibility(View.GONE);
        deviceListResume.setVisibility(View.VISIBLE);
      }
      builder.setView(layout);
      deviceSelectDialog = builder.create();

      deviceSelectDialog.show();

      final ListView clientListView = (ListView) deviceSelectDialog.findViewById(R.id.serverListView);

      adapter.setClients(VoiceControlForPlexApplication.getAllClients());
      clientListView.setAdapter(adapter);
      clientListView.setOnItemClickListener(new ListView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parentAdapter, View view, int position,
                                long id) {
          PlexClient s = (PlexClient) parentAdapter.getItemAtPosition(position);
          Logger.d("client clicked: %s", s.name);
          deviceSelectDialog.dismiss();
          if (onFinish != null)
            onFinish.onDeviceSelected(s, deviceListResume.isChecked());
        }

      });

    } else if(!subscribing) {
      if(castPlayerManager.mClient != null)
        client = castPlayerManager.mClient;
      else if(plexSubscription.mClient != null)
        client = plexSubscription.mClient;
//          }
      if(client == null) {
        Logger.d("Lost subscribed client.");
        setCastIconInactive();
      } else {
        View view = getLayoutInflater().inflate(R.layout.popup_connected_to_client, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(view);
        final AlertDialog subscribeDialog = builder.create();

        CheckBox resumeCheckbox = (CheckBox)view.findViewById(R.id.resumeCheckbox);
        resumeCheckbox.setChecked(prefs.get(Preferences.RESUME, false));
        resumeCheckbox.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            prefs.put(Preferences.RESUME, ((CheckBox) v).isChecked());
          }
        });
        TextView clientName = (TextView)view.findViewById(R.id.popupConnectedToClientName);
        clientName.setText(client.name);
        Button cancelButton = (Button)view.findViewById(R.id.popupConnectedToClientCancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (client.isCastClient)
              castPlayerManager.unsubscribe();
            else
              plexSubscription.unsubscribe();
            subscribeDialog.dismiss();
          }
        });
        if(client.isCastClient) {
          final SeekBar volumeSeekBar = (SeekBar)view.findViewById(R.id.volumeSeekBar);
          volumeSeekBar.setVisibility(View.VISIBLE);
          volumeSeekBar.setMax(100);
          volumeSeekBar.setProgress((int)(castPlayerManager.getVolume()*100));
          volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
              double v = ((double)progress) / 100;
              castPlayerManager.setVolume(v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
          });
          // React to volume button controls while this dialog is open
          subscribeDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
              boolean ret = MainActivity.this.dispatchKeyEvent(event);
              volumeSeekBar.setProgress((int)(castPlayerManager.getVolume()*100));
              return ret;
            }
          });
        }
        subscribeDialog.show();
      }
    }
  }

  // Make sure this is the method with just `Bundle` as the signature
  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    if(drawerToggle != null)
      drawerToggle.syncState();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    // Pass any configuration change to the drawer toggles
    drawerToggle.onConfigurationChanged(newConfig);
  }

  private void saveSettings() {
    prefs.put(Preferences.SERVER, gsonWrite.toJson(server));
    prefs.put(Preferences.CLIENT, gsonWrite.toJson(client));
    prefs.put(Preferences.RESUME, prefs.get(Preferences.RESUME, false));
  }

  public void deviceSelectDialogRefresh() {
    ListView serverListView = (ListView) deviceSelectDialog.findViewById(R.id.serverListView);
    PlexListAdapter adapter = (PlexListAdapter)serverListView.getAdapter();
    adapter.setClients(VoiceControlForPlexApplication.getAllClients());
    adapter.notifyDataSetChanged();
  }

  private class MediaRouterCallback extends MediaRouter.Callback {
    @Override
    public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo route) {
      super.onRouteRemoved(router, route);
      Logger.d("Cast Client %s has gone missing. Removing.", route.getName());
      if(VoiceControlForPlexApplication.castClients.containsKey(route.getName())) {
        VoiceControlForPlexApplication.castClients.remove(route.getName());
        if(deviceSelectDialog != null && deviceSelectDialog.isShowing()) {
          deviceSelectDialogRefresh();
        }
      }
    }

    @Override
    public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route)
    {
      Logger.d("onRouteAdded: %s", route);
      if(!VoiceControlForPlexApplication.castClients.containsKey(route.getName())) {
        VoiceControlForPlexApplication.castClients.remove(route.getName());
      }
      PlexClient client = new PlexClient();
      client.isCastClient = true;
      client.name = route.getName();
      client.product = route.getDescription();
      client.castDevice = CastDevice.getFromBundle(route.getExtras());
      client.machineIdentifier = client.castDevice.getDeviceId();
      VoiceControlForPlexApplication.castClients.put(client.name, client);
      Logger.d("Added cast client %s (%s)", client.name, client.machineIdentifier);
      if(deviceSelectDialog != null && deviceSelectDialog.isShowing()) {
        deviceSelectDialogRefresh();
      }
    }

    @Override
    public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
      Logger.d("onRouteSelected: %s", route);
    }

    @Override
    public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
      Logger.d("onRouteUnselected: %s", route);
    }
  }

  public void showAbout(final MenuItem item) {
    navigationViewMain.setSelected(false);
    AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
    LayoutInflater inflater = getLayoutInflater();
    View layout = inflater.inflate(R.layout.popup_about, null);
    alertDialog.setView(layout);
    alertDialog.show();
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        item.setChecked(false);
      }
    }, 500);

  }

  public void installShortcut(MenuItem item) {
    Intent intent = new Intent(this, ShortcutProviderActivity.class);
    startActivityForResult(intent, RESULT_SHORTCUT_CREATED);
  }

  public void donate(MenuItem item) {
    Intent intent = new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=UJF9QY9QELERG"));
    startActivity(intent);
  }

  public void setFeedback(MenuItem item) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    View view = getLayoutInflater().inflate(R.layout.popup_feedback, null);
    builder.setView(view);
    final AlertDialog dialog = builder.create();
    MultiStateToggleButton feedbackToggleButton = (MultiStateToggleButton)view.findViewById(R.id.feedbackToggleButton);
    boolean[] v = new boolean[2];
    v[prefs.get(Preferences.FEEDBACK, 1) == 0 ? 0 : 1] = true;
    feedbackToggleButton.setStates(v);
    feedbackToggleButton.setOnValueChangedListener(new ToggleButton.OnValueChangedListener() {
      @Override
      public void onValueChanged(int value) {
        prefs.put(Preferences.FEEDBACK, value);
        if(value == 0) {
          onVoiceFeedbackSelected(false);
        }
      }
    });
    MultiStateToggleButton errorsToggleButton = (MultiStateToggleButton)view.findViewById(R.id.errorsToggleButton);
    v = new boolean[2];
    v[prefs.get(Preferences.ERRORS, 1) == 0 ? 0 : 1] = true;
    errorsToggleButton.setStates(v);
    errorsToggleButton.setOnValueChangedListener(new ToggleButton.OnValueChangedListener() {
      @Override
      public void onValueChanged(int value) {
        prefs.put(Preferences.ERRORS, value);
        if(value == 0) {
          onVoiceFeedbackSelected(true);
        }
      }
    });
    dialog.show();
  }

  private void onVoiceFeedbackSelected(boolean errors) {
    Intent checkIntent = new Intent();
    checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
    tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
      @Override
      public void onInit(int i) {}
    });
    String engine = tts.getDefaultEngine();
    if (engine != null)
      checkIntent.setPackage(engine);
    settingErrorFeedback = errors;
    startActivityForResult(checkIntent, RESULT_VOICE_FEEDBACK_SELECTED);
  }

  public void installTasker(MenuItem item) {
    openAppInPlayStore("net.dinglisch.android.taskerm");
  }

  public void showChangelog(MenuItem item) {
    showWhatsNewDialog(true);
  }

  public void installUtter(MenuItem item) {
    openAppInPlayStore("com.brandall.nutter");
  }

  public void installAutoVoice(MenuItem item) {
    openAppInPlayStore("com.joaomgcd.autovoice");
  }

  public void purchaseWear(MenuItem item) {
    showWearPurchaseRequired();
  }

  public void emailDeviceLogs(MenuItem item) {
    if(VoiceControlForPlexApplication.getInstance().hasWear()) {
      receivedWearLogsResponse = false;
      new SendToDataLayerThread(WearConstants.GET_DEVICE_LOGS, this).start();
      Logger.d("requesting device logs from wear device");
      // Now start a 5 second timer. If receivedWearLogsResponse is not true, go ahead and email just the mobile device's log
      final Handler handler = new Handler();
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          if(receivedWearLogsResponse == false)
            emailDeviceLogs("");
        }
      }, 2000);
    } else {
      emailDeviceLogs("");
    }
  }

  // The passed 'wearLog' string is the contents of the wear device's log. If there is no wear device paired with the mobile device,
  // the passed string will be empty ("")
  //
  public void emailDeviceLogs(final String wearLog) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        try {
          boolean hasPermission = (ContextCompat.checkSelfPermission(MainActivity.this,
                  Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
          if(!hasPermission) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
          } else {
            Logger.d("Emailing device logs");
            Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Voice Control for Plex Android Logs");
            emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Build the body of the email
            StringBuilder body = new StringBuilder();
            body.append(String.format("Manufacturer: %s\n", Build.MANUFACTURER));
            body.append(String.format("Device: %s\n", Build.DEVICE));
            body.append(String.format("Model: %s\n", Build.MODEL));
            body.append(String.format("Product: %s\n", Build.PRODUCT));
            body.append(String.format("Version: %s\n", Build.VERSION.RELEASE));
            body.append(String.format("App Version: %s\n\n", getPackageManager().getPackageInfo(getPackageName(), 0).versionName));

            body.append(String.format("Logged in: %s\n\n", prefs.getString(Preferences.PLEX_USERNAME) != null ? "yes" : "no"));

            body.append("Description of the issue:\n\n");

            emailIntent.setType("application/octet-stream");

            emailIntent.putExtra(Intent.EXTRA_TEXT, body.toString());

            File tempDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/tmp");
            if (!tempDirectory.exists())
              tempDirectory.mkdirs();

            File tempFile = new File(tempDirectory, "/vcfp-log.txt");
            FileOutputStream fos = new FileOutputStream(tempFile);
            Writer out = new OutputStreamWriter(fos, "UTF-8");

            Process process = Runtime.getRuntime().exec("logcat -d *:V");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder log = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
              log.append(line);
              log.append(System.getProperty("line.separator"));
            }

            bufferedReader.close();

            out.write(log.toString());
            out.flush();
            out.close();

            ArrayList<Uri> uris = new ArrayList<Uri>();
            uris.add(Uri.parse("file://" + tempFile.getAbsolutePath()));
//            uris.add(FileProvider.getUriForFile(MainActivity.this, "com.atomjack.vcfp.fileprovider", tempFile));

            if (!wearLog.equals("")) {
              Logger.d("attaching wear log");
              tempFile = new File(tempDirectory, "/vcfp-wear-log.txt");
              fos = new FileOutputStream(tempFile);
              out = new OutputStreamWriter(fos, "UTF-8");
              out.write(wearLog);
              out.flush();
              out.close();
              uris.add(Uri.parse("file://" + tempFile.getAbsolutePath()));
//                uris.add(FileProvider.getUriForFile(MainActivity.this, "com.atomjack.vcfp.fileprovider", tempFile));
            }
            emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            startActivity(emailIntent);
          }
        } catch (final Exception ex) {
          Logger.e("Exception emailing device logs: %s", ex);
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              feedback.e("Error emailing device logs: %s", ex.getMessage());
            }
          });
        }
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void openAppInPlayStore(String packageName) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
    } catch (android.content.ActivityNotFoundException anfe) {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
    }
  }

  public void cinemaTrailers(MenuItem item) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    LayoutInflater inflater = getLayoutInflater();
    View layout = inflater.inflate(R.layout.popup_cinema_trailers, null);
    final ListView listView = (ListView)layout.findViewById(R.id.cinemaTrailersList);
    List<String> list = new ArrayList<>();
    list.add(getString(R.string.none));
    list.add("1");
    list.add("2");
    list.add("3");
    list.add("4");
    list.add("5");
    ArrayAdapter arrayAdapter = new ArrayAdapter(getApplicationContext(), android.R.layout.simple_list_item_single_choice, list);

    listView.setAdapter(arrayAdapter);
    int numTrailers = prefs.get(Preferences.NUM_CINEMA_TRAILERS, 0);
    listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    listView.setItemChecked(numTrailers, true);

    builder.setView(layout);

    final AlertDialog dialog = builder.create();
    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        CheckedTextView item = (CheckedTextView)view;
        item.setChecked(true);
        prefs.put(Preferences.NUM_CINEMA_TRAILERS, position);
        handler.postDelayed(new Runnable() {
          @Override
          public void run() {
            dialog.dismiss();
          }
        }, 500);
      }
    });
    dialog.show();

  }

  protected void showChromecastPurchase(PlexClient client, Runnable onSuccess) {
    postChromecastPurchaseClient = client;
    postChromecastPurchaseAction = onSuccess;
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    View view = getLayoutInflater().inflate(R.layout.popup_chromecast_purchase, null);
    builder.setView(view);
    final AlertDialog dialog = builder.setCancelable(false).create();
    TextView popupChromecastPurchaseMessage = (TextView)view.findViewById(R.id.popupChromecastPurchaseMessage);
    popupChromecastPurchaseMessage.setText(String.format(getString(R.string.must_purchase_chromecast), VoiceControlForPlexApplication.getChromecastPrice()));
    Button popupChromecastPurchaseOKButton = (Button)view.findViewById(R.id.popupChromecastPurchaseOKButton);
    popupChromecastPurchaseOKButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        dialog.cancel();
        VoiceControlForPlexApplication.getInstance().getIabHelper().launchPurchaseFlow(MainActivity.this, VoiceControlForPlexApplication.SKU_CHROMECAST, 10001, mPurchaseFinishedListener, VoiceControlForPlexApplication.SKU_TEST_PURCHASED == VoiceControlForPlexApplication.SKU_CHROMECAST ? VoiceControlForPlexApplication.getInstance().getEmailHash() : "");
      }
    });
    Button popupChromecastPurchaseNoButton = (Button)view.findViewById(R.id.popupChromecastPurchaseNoButton);
    popupChromecastPurchaseNoButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        dialog.cancel();
        setCastIconInactive();
      }
    });
    dialog.show();
  }

  protected void showWearPurchaseRequired() {
    showWearPurchase(R.string.wear_purchase_required, false);
  }

  protected void showWearPurchase() {
    showWearPurchase(R.string.wear_detected_can_purchase, true);
  }

  protected void showWearPurchase(int stringResource, final boolean showPurchaseFromMenu) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    View view = getLayoutInflater().inflate(R.layout.popup_wear_purchase_required, null);
    builder.setView(view);
    final AlertDialog dialog = builder.create();
    TextView wearPurchaseRequiredTitle = (TextView)view.findViewById(R.id.wearPurchaseRequiredTitle);
    wearPurchaseRequiredTitle.setText(String.format(getString(stringResource), VoiceControlForPlexApplication.getWearPrice()));
    Button wearPurchaseRequiredNoThanksButton = (Button)view.findViewById(R.id.wearPurchaseRequiredNoThanksButton);
    wearPurchaseRequiredNoThanksButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        dialog.cancel();
        prefs.put(Preferences.HAS_SHOWN_WEAR_PURCHASE_POPUP, true);
        if(showPurchaseFromMenu) {
          AlertDialog.Builder builder2 = new AlertDialog.Builder(MainActivity.this);
          View view = getLayoutInflater().inflate(R.layout.popup_wear_purchase_menu, null);
          builder2.setView(view).setCancelable(false);
          final AlertDialog dialog = builder2.create();
          Button popupWearPurchaseMenuOKButton = (Button)view.findViewById(R.id.popupWearPurchaseMenuOKButton);
          popupWearPurchaseMenuOKButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              dialog.cancel();
            }
          });
          dialog.show();
        }
      }
    });
    Button wearPurchaseRequiredOKButton = (Button)view.findViewById(R.id.wearPurchaseRequiredOKButton);
    wearPurchaseRequiredOKButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        prefs.put(Preferences.HAS_SHOWN_WEAR_PURCHASE_POPUP, true);
        dialog.cancel();
        VoiceControlForPlexApplication.getInstance().getIabHelper().flagEndAsync();
        VoiceControlForPlexApplication.getInstance().getIabHelper().launchPurchaseFlow(MainActivity.this,
                VoiceControlForPlexApplication.SKU_WEAR, 10001, mPurchaseFinishedListener,
                VoiceControlForPlexApplication.SKU_TEST_PURCHASED == VoiceControlForPlexApplication.SKU_WEAR ? VoiceControlForPlexApplication.getInstance().getEmailHash() : "");
      }
    });


    dialog.show();
/*
    new AlertDialog.Builder(MainActivity.this)
            .setMessage(String.format(getString(stringResource), VoiceControlForPlexApplication.getWearPrice()))
            .setCancelable(false)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                prefs.put(Preferences.HAS_SHOWN_WEAR_PURCHASE_POPUP, true);
                dialogInterface.cancel();
                VoiceControlForPlexApplication.getInstance().getIabHelper().launchPurchaseFlow(MainActivity.this,
                        VoiceControlForPlexApplication.SKU_WEAR, 10001, mPurchaseFinishedListener,
                        VoiceControlForPlexApplication.SKU_TEST_PURCHASED == VoiceControlForPlexApplication.SKU_WEAR ? VoiceControlForPlexApplication.getInstance().getEmailHash() : "");
              }
            })
            .setNeutralButton(R.string.no_thanks, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                prefs.put(Preferences.HAS_SHOWN_WEAR_PURCHASE_POPUP, true);
                if(showPurchaseFromMenu) {
                  new AlertDialog.Builder(MainActivity.this)
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
            */
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
        // TODO: this
        // Only reset the cast icon if we aren't subscribed (if we are, the only way to get here is through main client selection)
//        if(!isSubscribed())
//          setCastIconInactive();
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
        new SendToDataLayerThread(WearConstants.WEAR_PURCHASED, MainActivity.this).start();
      }
    }
  };

  public void showWearOptions(MenuItem item) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    LayoutInflater inflater = getLayoutInflater();
    View layout = inflater.inflate(R.layout.popup_wear_options, null);
    builder.setView(layout);
    final AlertDialog chooserDialog = builder.create();
    Button playPauseButton = (Button)layout.findViewById(R.id.wearOptionsPlayPauseButton);
    playPauseButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        DataMap dataMap = new DataMap();
        dataMap.putBoolean(WearConstants.PRIMARY_FUNCTION_VOICE_INPUT, true);
        new SendToDataLayerThread(WearConstants.SET_WEAR_OPTIONS, dataMap, MainActivity.this).start();
        chooserDialog.dismiss();
      }
    });
    Button voiceInputButton = (Button)layout.findViewById(R.id.wearOptionsVoiceInputButton);
    voiceInputButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        DataMap dataMap = new DataMap();
        dataMap.putBoolean(WearConstants.PRIMARY_FUNCTION_VOICE_INPUT, false);
        new SendToDataLayerThread(WearConstants.SET_WEAR_OPTIONS, dataMap, MainActivity.this).start();
        chooserDialog.dismiss();
      }
    });
    chooserDialog.show();
  }

  public void showChromecastVideoOptions(MenuItem item) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);

    View layout = getLayoutInflater().inflate(R.layout.popup_chromecast_video_options, null);
    builder.setView(layout);
    final AlertDialog chooserDialog = builder.create();

    Button popupChromecastOptionsRemoteButton = (Button)layout.findViewById(R.id.popupChromecastOptionsRemoteButton);
    popupChromecastOptionsRemoteButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        chooserDialog.dismiss();
        showChromecastVideoOptions(false);
      }
    });
    Button popupChromecastOptionsLocalButton = (Button)layout.findViewById(R.id.popupChromecastOptionsLocalButton);
    popupChromecastOptionsLocalButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        chooserDialog.dismiss();
        showChromecastVideoOptions(true);
      }
    });
    chooserDialog.show();
  }

  private void showChromecastVideoOptions(final boolean local) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    View view = getLayoutInflater().inflate(R.layout.popup_chromecast_video_options_detail, null);
    builder.setView(view);
    final AlertDialog dialog = builder.create();
    TextView chromecastVideoOptionsTitle = (TextView)view.findViewById(R.id.chromecastVideoOptionsTitle);
    chromecastVideoOptionsTitle.setText(local ? R.string.chromecast_video_local_full : R.string.chromecast_video_remote_full);
    RadioGroup chromecastVideoOptionsRadioGroup = (RadioGroup)view.findViewById(R.id.chromecastVideoOptionsRadioGroup);
    final CharSequence[] items = VoiceControlForPlexApplication.chromecastVideoOptions.keySet().toArray(new CharSequence[VoiceControlForPlexApplication.chromecastVideoOptions.size()]);
    int videoQuality = new ArrayList<>(VoiceControlForPlexApplication.chromecastVideoOptions.keySet()).indexOf(prefs.getString(local ? Preferences.CHROMECAST_VIDEO_QUALITY_LOCAL : Preferences.CHROMECAST_VIDEO_QUALITY_REMOTE));
    if(videoQuality == -1)
      videoQuality = new ArrayList<>(VoiceControlForPlexApplication.chromecastVideoOptions.keySet()).indexOf("8mbps 720p");
    LinearLayout.LayoutParams layoutParams = new RadioGroup.LayoutParams(
            RadioGroup.LayoutParams.WRAP_CONTENT,
            RadioGroup.LayoutParams.WRAP_CONTENT);
    for(int i=0;i<items.length;i++) {
      RadioButton button = (RadioButton)getLayoutInflater().inflate(R.layout.popup_chromecast_video_options_button, null);
      button.setText(items[i]);
      button.setId(i);
      chromecastVideoOptionsRadioGroup.addView(button, layoutParams);
    }
    chromecastVideoOptionsRadioGroup.check(videoQuality);
    chromecastVideoOptionsRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(RadioGroup group, int checkedId) {
        Logger.d("Checked %s", items[checkedId]);
        prefs.put(local ? Preferences.CHROMECAST_VIDEO_QUALITY_LOCAL : Preferences.CHROMECAST_VIDEO_QUALITY_REMOTE, (String)items[checkedId]);
        dialog.dismiss();
      }
    });
    dialog.show();
  }

  public void importTaskerProject(MenuItem item) {
    String xmlfile = "VoiceControlForPlex.prj.xml";

    try
    {
      AssetManager am = getAssets();
      InputStream is = am.open(xmlfile);
      int size = is.available();
      byte[] buffer = new byte[size];
      is.read(buffer);
      is.close();

      String xmlContents = new String(buffer);
      xmlContents = xmlContents.replace("%RECOGNITION_REGEX%", getString(R.string.pattern_recognition));
      buffer = xmlContents.getBytes();
      Logger.d("directory: %s", Environment.getExternalStorageDirectory());

      File f = new File(Environment.getExternalStorageDirectory() + "/" + xmlfile);
      FileOutputStream fos = new FileOutputStream(f);
      fos.write(buffer);
      fos.close();

      Logger.d("Wrote xml file");

      Intent i = new Intent();
      i.setAction(Intent.ACTION_VIEW);
      i.setDataAndType(Uri.fromFile(f), "text/xml");
      startActivityForResult(i, RESULT_TASKER_PROJECT_IMPORTED);
    } catch (Exception e) {
      Logger.d("Exception opening tasker profile xml: ");
      e.printStackTrace();
      return;
    }


  }

  @Override
  public void onUserInteraction() {
    super.onUserInteraction();
    userIsInteracting = true;
  }

  public void hidePurchaseWearMenuItem() {
    MenuItem wearItem = navigationViewMain.getMenu().findItem(R.id.menu_purchase_wear);
    wearItem.setVisible(false);
    MenuItem wearOptionsItem = navigationViewMain.getMenu().findItem(R.id.menu_wear_options);
    wearOptionsItem.setVisible(true);

  }

  private boolean hasValidAutoVoice() {
    try
    {
      if(hasValidTasker()) {
        PackageInfo pinfo = getPackageManager().getPackageInfo("com.joaomgcd.autovoice", 0);
        return true;
      }
    } catch(Exception e) {
      Logger.d("Exception getting autovoice version: " + e.getStackTrace());
    }
    return false;
  }

  private boolean hasValidUtter() {
    try
    {
      if(hasValidTasker()) {
        PackageInfo pinfo = getPackageManager().getPackageInfo("com.brandall.nutter", 0);
        return true;
      }
    } catch(Exception e) {
      Logger.d("Exception getting utter version: " + e.getStackTrace());
    }
    return false;
  }

  private boolean hasValidTasker() {
    PackageInfo pinfo;
    try
    {
      pinfo = getPackageManager().getPackageInfo("net.dinglisch.android.tasker", 0);
      return true;
    } catch(Exception e) {}
    try
    {
      pinfo = getPackageManager().getPackageInfo("net.dinglisch.android.taskerm", 0);
      return true;
    } catch(Exception e) {
      Logger.d("Exception getting tasker version: " + e.getStackTrace());
    }
    return false;
  }

  protected void setCastIconInactive() {
    Logger.d("[MainActivity] setCastIconInactive");
    try {
      castIconMenuItem.setIcon(R.drawable.mr_ic_media_route_holo_dark);
    } catch (Exception e) {}
  }

  protected void setCastIconActive() {
    Logger.d("[MainActivity] setCastIconActive");
    try {
      castIconMenuItem.setIcon(R.drawable.mr_ic_media_route_on_holo_dark);
    } catch (Exception e) {}
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    networkMonitor.unregister();
//    plexSubscription.removeListener((PlexPlayerFragment)playerFragment);
  }

  public void setStream(Stream stream) {
    client.setStream(stream);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    Logger.d("Saving instance state");
    outState.putParcelable(com.atomjack.shared.Intent.EXTRA_CLIENT, client);
    if(playerFragment != null && playerFragment.isVisible()) {
      getSupportFragmentManager().putFragment(outState, com.atomjack.shared.Intent.EXTRA_PLAYER_FRAGMENT, playerFragment);
    }
  }


  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    try {
      super.onRestoreInstanceState(savedInstanceState);
    } catch (Exception e) {}
    client = savedInstanceState.getParcelable(com.atomjack.shared.Intent.EXTRA_CLIENT);

  }

  @Override
  public void onDisconnected() {
    Logger.d("Disconnected");
    currentNetworkState = NetworkState.DISCONNECTED;

    // We have no network connection, so hide the cast button
    castIconMenuItem.setVisible(false);
  }

  @Override
  public void onConnected(int connectionType) {
    Logger.d("Connected with type %d", connectionType);
    // Only show the cast button if the previous state was disconnected.
    if(currentNetworkState == NetworkState.DISCONNECTED) {
      castIconMenuItem.setVisible(true);
    }

    if(connectionType == ConnectivityManager.TYPE_MOBILE)
      currentNetworkState = NetworkState.MOBILE;
    else if(connectionType == ConnectivityManager.TYPE_WIFI)
      currentNetworkState = NetworkState.WIFI;

    if(plexSubscription.isSubscribed()) {
      // If it's been more than 30 seconds since we last heard from the subscribed client, force a (non-heartbeat)
      // subscription request right now to refresh. It shouldn't be a heartbeat request in case the client
      // booted us off for being unreachable for 90 seconds.
      if(plexSubscription.timeLastHeardFromClient != null) {
        if((new Date().getTime() - plexSubscription.timeLastHeardFromClient.getTime()) / 1000 >= 30) {
          plexSubscription.subscribe(plexSubscription.getClient());
        }
      }
    }
  }

  @Override
  public void onLayoutNotFound() {
    // This is passed by PlayerFragment in the case where it is not able to tell which layout (tv/movie/music) to use. We should switch back to the main fragment
    switchToFragment(getMainFragment());
  }

  private MainFragment getMainFragment() {
    if(mainFragment == null)
      mainFragment = new MainFragment();
    return mainFragment;
  }

  private int getLayoutForMedia(PlexMedia media, PlayerState state) {
    if(playerFragment == null) {
      playerFragment = client.isCastClient ? new CastPlayerFragment() : new PlexPlayerFragment();
    } else if(client.isCastClient && playerFragment instanceof PlexPlayerFragment)
      playerFragment = new CastPlayerFragment();
    else if(!client.isCastClient && playerFragment instanceof CastPlayerFragment)
      playerFragment = new PlexPlayerFragment();

    playerFragment.setRetainInstance(true);
    playerFragment.setState(state);
    playerFragment.setPosition(Integer.parseInt(media.viewOffset)/1000); // View offset from PMS is in ms

    int layout = -1;
    if(media.isMovie())
      layout = R.layout.now_playing_movie;
    else if(media.isShow())
      layout = R.layout.now_playing_show;
    else if(media.isMusic())
      layout = R.layout.now_playing_music;
    return layout;
  }

  private void checkForMissingPlexEmail() {
    if(prefs.get(Preferences.PLEX_EMAIL, null) == null && authToken != null) {
      PlexHttpClient.getPlexAccount(authToken, new PlexHttpUserHandler() {
        @Override
        public void onSuccess(PlexUser user) {
          prefs.put(Preferences.PLEX_EMAIL, user.email);
          init();
        }

        @Override
        public void onFailure(int statusCode) {
          Logger.d("Failure: %d",statusCode);
        }
      });
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    double VOLUME_INCREMENT = 0.05;
    if(castPlayerManager.isSubscribed()) {
      int action = event.getAction();
      int keyCode = event.getKeyCode();
      if(keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
        if(action == KeyEvent.ACTION_DOWN) {
          double currentVolume = castPlayerManager.getVolume();
          if(currentVolume < 1.0) {
            castPlayerManager.setVolume(Math.min(currentVolume + VOLUME_INCREMENT, 1.0));
          }
        }
        return true;
      } else if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
        if(action == KeyEvent.ACTION_DOWN) {
          double currentVolume = castPlayerManager.getVolume();
          if(currentVolume > 0.0) {
            castPlayerManager.setVolume(Math.max(currentVolume - VOLUME_INCREMENT, 0.0));
          }
        }
        return true;
      }
    }
    return super.dispatchKeyEvent(event);
  }

  public void sendWearPlaybackChange(final PlayerState state, PlexMedia media) {
    if(VoiceControlForPlexApplication.getInstance().hasWear()) {
      Logger.d("[PlayerFragment] Sending Wear Notification: %s", state);
      final DataMap data = new DataMap();
      String msg = null;
      if (state == PlayerState.PLAYING) {
        data.putString(WearConstants.MEDIA_TYPE, media.getType());
        VoiceControlForPlexApplication.SetWearMediaTitles(data, media);
        msg = WearConstants.MEDIA_PLAYING;
      } else if (state == PlayerState.STOPPED) {
        msg = WearConstants.MEDIA_STOPPED;
      } else if (state == PlayerState.PAUSED) {
        msg = WearConstants.MEDIA_PAUSED;
        VoiceControlForPlexApplication.SetWearMediaTitles(data, media);
      }
      if (msg != null) {
        if (msg.equals(WearConstants.MEDIA_PLAYING)) {
          VoiceControlForPlexApplication.getWearMediaImage(media, new BitmapHandler() {
            @Override
            public void onSuccess(Bitmap bitmap) {
              DataMap binaryDataMap = new DataMap();
              binaryDataMap.putAll(data);
              binaryDataMap.putAsset(WearConstants.IMAGE, VoiceControlForPlexApplication.createAssetFromBitmap(bitmap));
              binaryDataMap.putString(WearConstants.PLAYBACK_STATE, state.name());
              new SendToDataLayerThread(WearConstants.RECEIVE_MEDIA_IMAGE, binaryDataMap, MainActivity.this).sendDataItem();
            }
          });
        }
        new SendToDataLayerThread(msg, data, this).start();
      }
    }
  }

  @Override
  public void onInit(int status) {
    if (status == TextToSpeech.SUCCESS) {
      final String pref = settingErrorFeedback ? Preferences.ERRORS_VOICE : Preferences.FEEDBACK_VOICE;
      if (availableVoices != null) {
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.popup_language_selector, null);
        adb.setView(view);
        final CharSequence items[] = availableVoices.toArray(new CharSequence[availableVoices.size()]);
        int selectedVoice = -1;
        String v = VoiceControlForPlexApplication.getInstance().prefs.get(pref, "Locale.US");
        if (availableVoices.indexOf(v) > -1)
          selectedVoice = availableVoices.indexOf(v);

        final AlertDialog dialog = adb.create();
        Button languageSelectorCancelButton = (Button)view.findViewById(R.id.languageSelectorCancelButton);
        languageSelectorCancelButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            dialog.cancel();
          }
        });

        RadioGroup languageSelectorRadioGroup = (RadioGroup)view.findViewById(R.id.languageSelectorRadioGroup);
        LinearLayout.LayoutParams layoutParams = new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.WRAP_CONTENT,
                RadioGroup.LayoutParams.WRAP_CONTENT);
        for(int i=0;i<items.length;i++) {
          RadioButton button = (RadioButton)getLayoutInflater().inflate(R.layout.popup_chromecast_video_options_button, null);
          button.setText(items[i]);
          button.setId(i);
          languageSelectorRadioGroup.addView(button, layoutParams);
        }
        languageSelectorRadioGroup.check(selectedVoice);
        languageSelectorRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(RadioGroup group, int checkedId) {
            VoiceControlForPlexApplication.getInstance().prefs.put(pref, items[checkedId].toString());
            dialog.dismiss();
          }
        });
        dialog.show();
      } else {
        VoiceControlForPlexApplication.getInstance().prefs.put(pref, "Locale.US");
      }
    }
  }
}
