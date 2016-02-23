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
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
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
import com.bugsense.trace.BugSenseHandler;
import com.cubeactive.martin.inscription.WhatsNewDialog;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.wearable.DataMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
        ActivityListener {

  public final static int RESULT_VOICE_FEEDBACK_SELECTED = 0;
  public final static int RESULT_TASKER_PROJECT_IMPORTED = 1;
  public final static int RESULT_SHORTCUT_CREATED = 2;

  public final static String ACTION_SHOW_NOW_PLAYING = "com.atomjack.vcfp.action_show_now_playing";

  private final static int SERVER_SCAN_INTERVAL = 1000*60*5; // scan for servers every 5 minutes

  private Handler handler;

  public final static String BUGSENSE_APIKEY = "879458d0";

  private DrawerLayout mDrawer;
  private Toolbar toolbar;
  private NavigationView navigationViewMain;
  private ActionBarDrawerToggle drawerToggle;
  private boolean mainNavigationItemsVisible = true;

  private String authToken;

  protected static final int REQUEST_WRITE_STORAGE = 112;

  public static String USER_THUMB_KEY = "user_thumb_key";

  // Whether or not we received device logs from a wear device. This will allow a timer to be run in case wear support has
  // been purchased, but no wear device is paired. When this happens, we'll go ahead and email just the mobile device's logs
  //
  private boolean receivedWearLogsResponse = false;

  // the currently selected server and client
  private PlexServer server;
  private PlexClient client;

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

  NavigationView navigationFooter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Logger.d("[NewMainActivity] onCreate");

//    B​aseCastManager.checkGooglePlayServices(this);

    if(savedInstanceState != null) {
      client = savedInstanceState.getParcelable(com.atomjack.shared.Intent.EXTRA_CLIENT);
      playerFragment = (PlayerFragment)getSupportFragmentManager().getFragment(savedInstanceState, com.atomjack.shared.Intent.EXTRA_PLAYER_FRAGMENT);
      Logger.d("playerFragment: %s", playerFragment);
    } else {
      Logger.d("savedInstanceState is null");
    }

    prefs = VoiceControlForPlexApplication.getInstance().prefs;
    feedback = new Feedback(this);

    authToken = VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.AUTHENTICATION_TOKEN);

    networkMonitor = new NetworkMonitor(this);
    VoiceControlForPlexApplication.getInstance().setNetworkChangeListener(this);

    currentNetworkState = NetworkState.getCurrentNetworkState(this);

    plexSubscription = VoiceControlForPlexApplication.getInstance().plexSubscription;

    castPlayerManager = VoiceControlForPlexApplication.getInstance().castPlayerManager;

    doingFirstTimeSetup = !prefs.get(Preferences.FIRST_TIME_SETUP_COMPLETED, false);
    // If auth token has already been set even though first time setup isn't complete, assume user
    // has upgraded
    if(authToken != null && doingFirstTimeSetup) {
      doingFirstTimeSetup = false;
      prefs.put(Preferences.FIRST_TIME_SETUP_COMPLETED, true);
    }

    // If plex email hasn't been saved, fetch it and refresh the navigation drawer when done
    checkForMissingPlexEmail();

    setContentView(R.layout.new_activity_main);

    if(!plexSubscription.isSubscribed() && !castPlayerManager.isSubscribed()) {
      Logger.d("Not subscribed: %s", plexSubscription.mClient);
      // In case the notification is still up due to a crash
      VoiceControlForPlexApplication.getInstance().cancelNotification();
    }

    init();
    doAutomaticDeviceScan();
    if(plexSubscription.isSubscribed() || castPlayerManager.isSubscribed())
      setCastIconActive();
    else
      Logger.d("Not subscribed");
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
  public void onBackPressed() {
    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_MAIN);
    intent.addCategory(Intent.CATEGORY_HOME);
    startActivity(intent);
  }

  private PlexSubscriptionListener plexSubscriptionListener = new PlexSubscriptionListener() {
    @Override
    public void onSubscribed(final PlexClient client) {
      Logger.d("[NewMainActivity] onSubscribed");

      VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SUBSCRIBED_CLIENT, gsonWrite.toJson(client));

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
    public void onMediaChanged(PlexMedia media) {
      Logger.d("[NewMainActivity] onMediaChanged: %s", media.getTitle());

      // TODO: Update everything in playerFragment
    }

    @Override
    public void onPlayStarted(PlexMedia media, PlayerState state) {
      Logger.d("[NewMainActivity] onPlayStarted: %s", media.getTitle());
      int layout = getLayoutForMedia(media, state);

      if(layout != -1) {
        if (client.isCastClient && false) {
          // TODO: Init cast client

        } else {
          playerFragment.init(layout, client, media, plexSubscriptionListener);
        }
        switchToFragment(playerFragment);
      }
    }

    @Override
    public void onStateChanged(PlexMedia media, PlayerState state) {
      Logger.d("[NewMainActivity] onStateChanged: %s", state);
      if(playerFragment != null && playerFragment.isVisible()) {
        if(state == PlayerState.STOPPED) {
          Logger.d("[NewMainActivity] onStopped");
          switchToFragment(getMainFragment());
          VoiceControlForPlexApplication.getInstance().prefs.remove(Preferences.SUBSCRIBED_CLIENT);
        } else {
          playerFragment.setState(state);
        }
      } else {
        Logger.d("Got state change to %s, but for some reason playerFragment is null", state);
         getLayoutForMedia(media, state);
      }

    }

    @Override
    public void onSubscribeError(String message) {
      Logger.d("[NewMainActivity] onSubscribeError");
      setCastIconInactive();
      subscribing = false;
      feedback.e(String.format(getString(R.string.cast_connect_error), client.name));
    }

    @Override
    public void onUnsubscribed() {
      Logger.d("[NewMainActivity] unsubscribed");
      setCastIconInactive();
      VoiceControlForPlexApplication.getInstance().prefs.remove(Preferences.SUBSCRIBED_CLIENT);
      switchToFragment(getMainFragment());
      // TODO: Implement
//    sendWearPlaybackChange();
      feedback.m(R.string.disconnected);
    }
  };

  private void switchToFragment(Fragment fragment) {
    getSupportFragmentManager().beginTransaction().replace(R.id.flContent, fragment).commit();
  }

  private void init() {
    handler = new Handler();

    if(BuildConfig.USE_BUGSENSE)
      BugSenseHandler.initAndStartSession(getApplicationContext(), BUGSENSE_APIKEY);

    // Set a Toolbar to replace the ActionBar.
    toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);

    Fragment fragment;
    if (!doingFirstTimeSetup) { // TODO: check for authToken here - if it is defined, and first time setup has not been completed, user has upgraded, so bypass setup

      Logger.d("Intent action: %s", getIntent().getAction());
      if(getIntent().getAction() != null && getIntent().getAction().equals(ACTION_SHOW_NOW_PLAYING)) {
        handleShowNowPlayingIntent(getIntent());
      } else {

        Logger.d("Loading main fragment");

        fragment = playerFragment != null ? playerFragment : getMainFragment();

        // Only show the what's new dialog if this is not the first time the app is run
        final WhatsNewDialog whatsNewDialog = new WhatsNewDialog(this);
        whatsNewDialog.show();

        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(BuildConfig.CHROMECAST_APP_ID))
                .build();
        mMediaRouterCallback = new MediaRouterCallback();
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);

        server = gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SERVER, ""), PlexServer.class);
        if (server == null)
          server = new PlexServer(getString(R.string.scan_all));

        client = gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.CLIENT, ""), PlexClient.class);


        setupNavigationDrawer();
        switchToFragment(fragment);
      }
    } else {
      fragment = new SetupFragment();
      switchToFragment(fragment);
    }


//    getSupportFragmentManager().beginTransaction().replace(R.id.flContent, fragment).commit();


  }

  @Override
  protected void onPause() {
    super.onPause();
    Logger.d("[NewMainActivity] onPause");
    if (isFinishing()) {
      mMediaRouter.removeCallback(mMediaRouterCallback);
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    Logger.d("[NewMainActivity] onStop");
    if(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SERVER_SCAN_FINISHED, true) == false) {
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
    Logger.d("[NewMainActivity] onRestart");
  }

  private Runnable autoDisconnectPlayerTimer = new Runnable() {
    @Override
    public void run() {
      if(playerFragment.isVisible())
        switchToFragment(mainFragment);
    }
  };

  @Override
  protected void onResume() {
    super.onResume();
    Logger.d("[NewMainActivity] onResume, interacting: %s", userIsInteracting);
    plexSubscription.setListener(plexSubscriptionListener);
    castPlayerManager.setListener(plexSubscriptionListener);
    if(!doingFirstTimeSetup) {
      mDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    } else
      mDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    if(playerFragment != null && playerFragment.isVisible()) {
      Logger.d("Setting auto disconnect for 3 seconds");
      handler.postDelayed(autoDisconnectPlayerTimer, 3000);
    }
  }

  private void doAutomaticDeviceScan() {
    if(BuildConfig.CHROMECAST_REQUIRES_PURCHASE) {
      Logger.d("Doing automatic device scan");
      // Kick off a scan for servers, if it's been more than five minutes since the last one.
      // We'll do this every five, to keep the list up to date. Also, if the last server scan didn't
      // finish, kick off another one right now instead (another scan in 5 minutes will be queued up when that one finishes).
      int s = getSecondsSinceLastServerScan();
      Logger.d("It's been %d seconds since last scan, last scan finished: %s", s, VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SERVER_SCAN_FINISHED, true));
      if (s >= (SERVER_SCAN_INTERVAL / 1000) || VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SERVER_SCAN_FINISHED, true) == false) {
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

    if(requestCode == RESULT_SHORTCUT_CREATED) {
      if(resultCode == RESULT_OK) {
        data.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        sendBroadcast(data);

        feedback.m(getString(R.string.shortcut_created));
      }
    }
  }

  public void mainLoadingBypassLogin(View v) {
    prefs.put(Preferences.FIRST_TIME_SETUP_COMPLETED, true);
    doingFirstTimeSetup = false;
    init();
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
    alertDialogBuilder.setTitle(R.string.pin_title);
    alertDialogBuilder.setMessage(String.format(getString(R.string.pin_message), pin.code));
    alertDialogBuilder
            .setCancelable(false)
            .setNegativeButton("Cancel",
                    new DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        fetchPinTask.getFuture().cancel(false);
                      }
                    }
            )
            .setNeutralButton("Manual", new DialogInterface.OnClickListener() {
              @Override
              public void onClick(final DialogInterface dialog, int id) {
                dialog.dismiss();
                fetchPinTask.getFuture().cancel(false);
                showManualLogin();
              }
            });


    // create and show an alert dialog
    final AlertDialog pinAlert = alertDialogBuilder.create();
    pinAlert.show();

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
              VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.AUTHENTICATION_TOKEN, authToken);
              PlexHttpClient.signin(authToken, new PlexHttpUserHandler() {
                @Override
                public void onSuccess(PlexUser user) {
                  Logger.d("Got user: %s", user.username);
                  VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.PLEX_USERNAME, user.username);
                  VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.PLEX_EMAIL, user.email);
                  if(doingFirstTimeSetup) {
                    showFindingPlexClientsAndServers();
                    refreshServers.run();
                    refreshClients.run();
                  } else {
                    setupNavigationDrawer();
                    refreshServers.run();
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
                  // TODO: switch login
//                  switchLogin();

                  /*
                  PlexScannerService.refreshResources(authToken, new PlexScannerService.RefreshResourcesResponseHandler() {
                    @Override
                    public void onSuccess() {
                      feedback.t(R.string.servers_refreshed);
                    }

                    @Override
                    public void onFailure(int statusCode) {
                      feedback.e(R.string.remote_scan_error);
                    }
                  });
                  */
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
            .setTitle(R.string.finding_plex_clients_and_servers)
            .setCancelable(false)
            .setView(layout)
            .create();
    alertDialog.show();
  }

  private void showManualLogin() {
    LayoutInflater layoutInflater = LayoutInflater.from(MainActivity.this);
    View promptView = layoutInflater.inflate(R.layout.login, null);
    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
    alertDialogBuilder.setView(promptView);
    alertDialogBuilder.setTitle(R.string.login_title);
    alertDialogBuilder.setMessage(R.string.login_message);
    final EditText usernameInput = (EditText) promptView.findViewById(R.id.usernameInput);
    final EditText passwordInput = (EditText) promptView.findViewById(R.id.passwordInput);
    alertDialogBuilder
            .setCancelable(true)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(final DialogInterface dialog, int id) {
              }
            })
            .setNeutralButton(R.string.button_pin, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(final DialogInterface dialog, int id) {
                dialog.dismiss();
                showLogin();
              }
            })
            .setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                      }
                    }
            );


    // create an alert dialog
    final AlertDialog alertD = alertDialogBuilder.create();

    alertD.show();

    Button b = alertD.getButton(DialogInterface.BUTTON_POSITIVE);
    b.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        PlexHttpClient.signin(usernameInput.getText().toString(), passwordInput.getText().toString(), new PlexHttpUserHandler() {
          @Override
          public void onSuccess(PlexUser user) {
            VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.AUTHENTICATION_TOKEN, user.authenticationToken);
            authToken = user.authenticationToken;
            VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.PLEX_USERNAME, user.username);
            VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.PLEX_EMAIL, user.email);
            feedback.m(R.string.logged_in);
            if(doingFirstTimeSetup) {
              showFindingPlexClientsAndServers();
              refreshServers.run();
              refreshClients.run();
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
  }

  public void logout(MenuItem item) {
    Logger.d("logging out");
    // ...

    VoiceControlForPlexApplication.getInstance().prefs.remove(Preferences.AUTHENTICATION_TOKEN);
    VoiceControlForPlexApplication.getInstance().prefs.remove(Preferences.PLEX_USERNAME);
    authToken = null;

    // TODO: If the currently selected server is not local, reset it to scan all. (MainActivity:541)

    // Remove any non-local servers from our list
    for(PlexServer s : VoiceControlForPlexApplication.servers.values()) {
      if(!s.local)
        VoiceControlForPlexApplication.servers.remove(s.name);
    }
    refreshNavServers();

    VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SAVED_SERVERS, gsonWrite.toJson(VoiceControlForPlexApplication.servers));
    saveSettings();

    // Refresh the navigation drawer
    setupNavigationDrawer();

    feedback.m(R.string.logged_out);
  }

  private void setupNavigationDrawer() {

    mDrawer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    mDrawer.setDrawerListener(drawerListener);
    drawerToggle = new ActionBarDrawerToggle(this, mDrawer, toolbar, R.string.drawer_open, R.string.drawer_close);

    // Find our drawer view
    navigationViewMain = (NavigationView) findViewById(R.id.navigationViewMain);
    Logger.d("navigationViewMain: %s", navigationViewMain);


    // Footer view
    navigationFooter = (NavigationView) findViewById(R.id.navigationViewFooter);
    ViewGroup.LayoutParams layoutParams = navigationFooter.getLayoutParams();
    TypedValue value = new TypedValue();
    getTheme().resolveAttribute(android.R.attr.listPreferredItemHeight, value, true);
    DisplayMetrics metrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(metrics);
    layoutParams.height = (int)value.getDimension(metrics)*navigationFooter.getMenu().size();
    navigationFooter.setLayoutParams(layoutParams);

    View headerView = LayoutInflater.from(this).inflate(R.layout.nav_header_dummy, null);
    navigationFooter.addHeaderView(headerView);
    navigationFooter.getHeaderView(0).setVisibility(View.GONE);

    drawerToggle.syncState();

    if(navigationViewMain.getHeaderView(0) != null)
      navigationViewMain.removeHeaderView(navigationViewMain.getHeaderView(0));

    refreshNavServers();

    if(authToken != null) {
      navigationViewMain.inflateHeaderView(R.layout.nav_header_logged_in);
      if(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_EMAIL) != null) {
        setUserThumb();
        Logger.d("Username = %s", VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME));
        final View navHeader = navigationViewMain.getHeaderView(0);

        TextView navHeaderUsername = (TextView)navHeader.findViewById(R.id.navHeaderUsername);
        navHeaderUsername.setText(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME));

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
      navigationViewMain.getMenu().findItem(R.id.nav_login).setVisible(true);
    }
  }

  private int getSecondsSinceLastServerScan() {
    Date now = new Date();
    Date lastServerScan = new Date(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.LAST_SERVER_SCAN, 0l));
    Logger.d("now: %s", now);
    Logger.d("lastServerScan: %s", lastServerScan);
    return (int)((now.getTime() - lastServerScan.getTime())/1000);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Logger.d("NewMainActivity onNewIntent: %s", intent.getAction());

    if(intent.getAction() != null) {
      if (intent.getAction().equals(PlexScannerService.ACTION_SERVER_SCAN_FINISHED)) {
        HashMap<String, PlexServer> s = (HashMap<String, PlexServer>) intent.getSerializableExtra(com.atomjack.shared.Intent.EXTRA_SERVERS);
        Logger.d("[NewMainActivity] finished scanning for servers, have %d servers", s.size());
        // Save the fact that we've finished this server scan
        VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SERVER_SCAN_FINISHED, true);
        VoiceControlForPlexApplication.servers = new ConcurrentHashMap<>(s);
        VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SAVED_SERVERS, gsonWrite.toJson(VoiceControlForPlexApplication.servers));
        if(doingFirstTimeSetup) {
          firstTimeSetupServerScanFinished = true;
          if(firstTimeSetupClientScanFinished) {
            doingFirstTimeSetup = false;
            prefs.put(Preferences.FIRST_TIME_SETUP_COMPLETED, true);
            alertDialog.dismiss();
            init();
            doAutomaticDeviceScan();
          }
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
          VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SAVED_CLIENTS, gsonWrite.toJson(VoiceControlForPlexApplication.clients));
          if (doingFirstTimeSetup) {
            firstTimeSetupClientScanFinished = true;
            if (firstTimeSetupServerScanFinished) {
              doingFirstTimeSetup = false;
              prefs.put(Preferences.FIRST_TIME_SETUP_COMPLETED, true);
              alertDialog.dismiss();
              init();
              doAutomaticDeviceScan();
            }
          }
        }
        if(onClientRefreshFinished != null) {
          onClientRefreshFinished.run();
        }

      } else if(intent.getAction().equals(ACTION_SHOW_NOW_PLAYING)) {
        handleShowNowPlayingIntent(intent);
      }
    }
  }

  private void handleShowNowPlayingIntent(Intent intent) {
    client = intent.getParcelableExtra(com.atomjack.shared.Intent.EXTRA_CLIENT);
    PlexMedia media = intent.getParcelableExtra(com.atomjack.shared.Intent.EXTRA_MEDIA);
    Logger.d("[NewMainActivity] show now playing: %s", media.getTitle());
    boolean fromWear = intent.getBooleanExtra(WearConstants.FROM_WEAR, false);
    PlayerState state;
    if(client.isCastClient) {
      // TODO: Get current state from CastPlayerManager
      state = PlayerState.STOPPED;
    } else {
      state = plexSubscription.getCurrentState();
      plexSubscription.subscribe(client);
    }
    int layout = getLayoutForMedia(media, state);
    Logger.d("Layout: %d", layout);
    if(layout != -1) {
      playerFragment.init(layout, client, media, plexSubscriptionListener);
      switchToFragment(playerFragment);
    }
  }

  private Runnable refreshServers = new Runnable() {
    @Override
    public void run() {
      // First, save the fact that we have started but not yet finished this server scan. On startup, we'll check for this and if it hasn't finished, kick off
      // a new scan right away.
      Logger.d("Refreshing servers");
      VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SERVER_SCAN_FINISHED, false);
      Intent scannerIntent = new Intent(MainActivity.this, PlexScannerService.class);
      scannerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      scannerIntent.putExtra(PlexScannerService.CLASS, MainActivity.class);
      scannerIntent.setAction(PlexScannerService.ACTION_SCAN_SERVERS);
      startService(scannerIntent);
      VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.LAST_SERVER_SCAN, new Date().getTime());
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
        server = s;
        if(mainFragment.isVisible())
          mainFragment.setServer(server);
        saveSettings();
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
//      Logger.d("Adding %s to menu (%d)", thisServer.name, id);
      MenuItem item = menu.add(Menu.NONE, id, id, thisServer.owned ? thisServer.name : thisServer.sourceTitle);
      item.setIcon(R.drawable.menu_server);
      item.setCheckable(true);
      item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
      LinearLayout layout = (LinearLayout)getLayoutInflater().inflate(thisServer.owned ? R.layout.nav_server_list_sections : R.layout.nav_server_list_sections_unowned, null);
      int numSections = server.movieSections.size() + server.tvSections.size() + server.musicSections.size();
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

  public void navMenuSettings(MenuItem item) {
    setNavGroup(R.menu.nav_items_settings);
  }

  public void navMenuHelp(MenuItem item) {
    AlertDialog.Builder usageDialog = new AlertDialog.Builder(this);
    usageDialog.setTitle(R.string.help_usage_button);
    usageDialog.setMessage(R.string.help_usage);
    usageDialog.setPositiveButton(R.string.got_it, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        dialog.dismiss();
      }
    });
    usageDialog.show();
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
    FrameLayout layout = (FrameLayout)v;
    final ImageView refreshButton = (ImageView)layout.findViewById(R.id.serverListRefreshButton);
    refreshButton.setVisibility(View.GONE);
    final ProgressBar refreshSpinner = (ProgressBar)layout.findViewById(R.id.serverListRefreshSpinner);
    refreshSpinner.setVisibility(View.VISIBLE);
    onServerRefreshFinished = new Runnable() {
      @Override
      public void run() {
        refreshButton.setVisibility(View.VISIBLE);
        refreshSpinner.setVisibility(View.GONE);
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
    final Bitmap bitmap = VoiceControlForPlexApplication.getInstance().getCachedBitmap(USER_THUMB_KEY);
    if(bitmap == null && !skipThumb) {
      fetchUserThumb();
    } else {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          View navHeader = navigationViewMain.getHeaderView(0);
          ImageView imageView = (ImageView) navHeader.findViewById(R.id.navHeaderUserIcon);
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
          String url = String.format("http://www.gravatar.com/avatar/%s?s=60", Utils.md5(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_EMAIL)));
          Logger.d("url: %s", url);
          byte[] imageData = PlexHttpClient.getSyncBytes(url);
          Logger.d("got %d bytes", imageData.length);
          InputStream is = new ByteArrayInputStream(imageData);
          is.reset();
          VoiceControlForPlexApplication.getInstance().mSimpleDiskCache.put(USER_THUMB_KEY, is);
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
    if (drawerToggle.onOptionsItemSelected(item)) {
      return true;
    }
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


  // This is the default action that will be taken when a user subscribes to a client via the UI.
  private ScanHandler onClientChosen = new ScanHandler() {
    @Override
    public void onDeviceSelected(PlexDevice device, boolean resume) {
      if(device != null) {
        subscribing = true;
        PlexClient clientSelected = (PlexClient)device;
        setClient(clientSelected);

        // Start animating the action bar icon
//        final MenuItem castIcon = menu.findItem(R.id.action_cast);
        castIconMenuItem.setIcon(R.drawable.mr_ic_media_route_connecting_holo_dark);
        AnimationDrawable ad = (AnimationDrawable) castIconMenuItem.getIcon();
        ad.start();

        if (clientSelected.isCastClient) {
          // TODO: chromecast stuff
          if(VoiceControlForPlexApplication.getInstance().hasChromecast()) {
            client = clientSelected;
            Logger.d("[NewMainActivity] subscribing to %s", client.name);
            castPlayerManager.subscribe(client);
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

  protected void setClient(PlexClient _client) {
    Logger.d("[NewMainActivity] setClient");
    client = _client;
    VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.CLIENT, gsonWrite.toJson(_client));
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
              adapter.setClients(VoiceControlForPlexApplication.clients);
              adapter.notifyDataSetChanged();
              onClientRefreshFinished = null;
            }
          };
          Logger.d("Refreshing");
          //deviceListRefreshSpinner
          button.setVisibility(View.GONE);
          spinnerImage.setVisibility(View.VISIBLE);
          handler.removeCallbacks(refreshClients);
          refreshClients.run();

        }
      });

      if(VoiceControlForPlexApplication.getAllClients().size() == 0)
        layout.findViewById(R.id.deviceSelectNoDevicesFound).setVisibility(View.VISIBLE);
      else
        layout.findViewById(R.id.deviceSelectNoDevicesFound).setVisibility(View.GONE);
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
          CheckBox resumeCheckbox = (CheckBox) deviceSelectDialog.findViewById(R.id.serverListResume);
          if (onFinish != null)
            onFinish.onDeviceSelected(s, resumeCheckbox.isChecked());
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
        AlertDialog.Builder subscribeDialog = new AlertDialog.Builder(this)
                .setTitle(client.name)
                .setIcon(R.drawable.mr_ic_media_route_on_holo_dark)
                .setNegativeButton(R.string.disconnect, new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialogInterface, int i) {
                    if (client.isCastClient)
                      castPlayerManager.unsubscribe();
                    else
                      plexSubscription.unsubscribe();
                    dialogInterface.dismiss();
                  }
                });
        if (client.isCastClient) {
          View subscribeVolume = LayoutInflater.from(this).inflate(R.layout.connected_popup, null);
          subscribeDialog.setView(subscribeVolume);
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
    VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SERVER, gsonWrite.toJson(server));
    VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.CLIENT, gsonWrite.toJson(client));
    VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.RESUME, VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false));
  }

  private class MediaRouterCallback extends MediaRouter.Callback {
    @Override
    public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo route) {
      super.onRouteRemoved(router, route);
      Logger.d("Cast Client %s has gone missing. Removing.", route.getName());
      if(VoiceControlForPlexApplication.castClients.containsKey(route.getName())) {
        VoiceControlForPlexApplication.castClients.remove(route.getName());
        VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SAVED_CAST_CLIENTS, gsonWrite.toJson(VoiceControlForPlexApplication.castClients));
        // If the "select a plex client" dialog is showing, refresh the list of clients
        // TODO: Refresh device dialog if needed
//        if(localScan.isDeviceDialogShowing()) {
//          localScan.deviceSelectDialogRefresh();
//        }
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
      VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SAVED_CAST_CLIENTS, gsonWrite.toJson(VoiceControlForPlexApplication.castClients));
      // If the "select a plex client" dialog is showing, refresh the list of clients
      // TODO: implement this?
//      if(deviceSelectDialog != null && deviceSelectDialog.isShowing()) {
//        deviceSelectDialogRefresh();
//      }
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
    AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this)
            .setTitle(R.string.app_name)
            .setMessage(R.string.about_text);

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

  public void installTasker(MenuItem item) {
    openAppInPlayStore("net.dinglisch.android.taskerm");
  }

  public void showChangelog(MenuItem item) {
    final WhatsNewDialog whatsNewDialog = new WhatsNewDialog(this);
    whatsNewDialog.forceShow();
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

            body.append(String.format("Logged in: %s\n\n", VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME) != null ? "yes" : "no"));

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
    builder.setTitle(getString(R.string.cinema_trailers_title));
    final CharSequence[] items = {getString(R.string.none), "1", "2", "3", "4", "5"};
    int numTrailers = VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.NUM_CINEMA_TRAILERS, 0);
    builder.setSingleChoiceItems(items, numTrailers, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        Logger.d("clicked %d", which);
        VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.NUM_CINEMA_TRAILERS, which);
        dialog.dismiss();
      }
    });
    builder.create().show();
  }

  protected void showChromecastPurchase(PlexClient client, Runnable onSuccess) {
    postChromecastPurchaseClient = client;
    postChromecastPurchaseAction = onSuccess;
    new AlertDialog.Builder(this)
            .setMessage(String.format(getString(R.string.must_purchase_chromecast), VoiceControlForPlexApplication.getChromecastPrice()))
            .setCancelable(false)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
                VoiceControlForPlexApplication.getInstance().getIabHelper().launchPurchaseFlow(MainActivity.this, VoiceControlForPlexApplication.SKU_CHROMECAST, 10001, mPurchaseFinishedListener, VoiceControlForPlexApplication.SKU_TEST_PURCHASED == VoiceControlForPlexApplication.SKU_CHROMECAST ? VoiceControlForPlexApplication.getInstance().getEmailHash() : "");
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
    new AlertDialog.Builder(MainActivity.this)
            .setMessage(String.format(getString(stringResource), VoiceControlForPlexApplication.getWearPrice()))
            .setCancelable(false)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.HAS_SHOWN_WEAR_PURCHASE_POPUP, true);
                dialogInterface.cancel();
                VoiceControlForPlexApplication.getInstance().getIabHelper().launchPurchaseFlow(MainActivity.this,
                        VoiceControlForPlexApplication.SKU_WEAR, 10001, mPurchaseFinishedListener,
                        VoiceControlForPlexApplication.SKU_TEST_PURCHASED == VoiceControlForPlexApplication.SKU_WEAR ? VoiceControlForPlexApplication.getInstance().getEmailHash() : "");
              }
            })
            .setNeutralButton(R.string.no_thanks, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.HAS_SHOWN_WEAR_PURCHASE_POPUP, true);
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
    AlertDialog.Builder chooserDialog = new AlertDialog.Builder(this);
    chooserDialog.setTitle(R.string.wear_primary_function);
    chooserDialog.setMessage(R.string.wear_primary_function_option_description);
    chooserDialog.setPositiveButton(R.string.voice_input, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        DataMap dataMap = new DataMap();
        dataMap.putBoolean(WearConstants.PRIMARY_FUNCTION_VOICE_INPUT, true);
        new SendToDataLayerThread(WearConstants.SET_WEAR_OPTIONS, dataMap, MainActivity.this).start();
        dialog.dismiss();
      }
    });
    chooserDialog.setNeutralButton(R.string.play_pause, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        DataMap dataMap = new DataMap();
        dataMap.putBoolean(WearConstants.PRIMARY_FUNCTION_VOICE_INPUT, false);
        new SendToDataLayerThread(WearConstants.SET_WEAR_OPTIONS, dataMap, MainActivity.this).start();
        dialog.dismiss();
      }
    });
    chooserDialog.show();
  }

  public void showChromecastVideoOptions(MenuItem item) {
    AlertDialog.Builder chooserDialog = new AlertDialog.Builder(this);
    chooserDialog.setTitle(R.string.chromecast_video_options_header);
//		chooserDialog.setMessage(R.string.wear_primary_function_option_description);
    chooserDialog.setPositiveButton(R.string.chromecast_video_local, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        showChromecastVideoOptions(true);
        dialog.dismiss();
      }
    });
    chooserDialog.setNeutralButton(R.string.chromecast_video_remote, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        showChromecastVideoOptions(false);
        dialog.dismiss();
      }
    });
    chooserDialog.show();
  }

  private void showChromecastVideoOptions(final boolean local) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(local ? getString(R.string.chromecast_video_local_full) : getString(R.string.chromecast_video_remote_full));
    final CharSequence[] items = VoiceControlForPlexApplication.chromecastVideoOptions.keySet().toArray(new CharSequence[VoiceControlForPlexApplication.chromecastVideoOptions.size()]);
    int videoQuality = new ArrayList<>(VoiceControlForPlexApplication.chromecastVideoOptions.keySet()).indexOf(VoiceControlForPlexApplication.getInstance().prefs.getString(local ? Preferences.CHROMECAST_VIDEO_QUALITY_LOCAL : Preferences.CHROMECAST_VIDEO_QUALITY_REMOTE));
    if(videoQuality == -1)
      videoQuality = new ArrayList<>(VoiceControlForPlexApplication.chromecastVideoOptions.keySet()).indexOf("8mbps 1080p");
    Logger.d("video quality: %d", videoQuality);
    builder.setSingleChoiceItems(items, videoQuality, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        Logger.d("clicked %d (%s) (%s)", which, (String)items[which], local);
        VoiceControlForPlexApplication.getInstance().prefs.put(local ? Preferences.CHROMECAST_VIDEO_QUALITY_LOCAL : Preferences.CHROMECAST_VIDEO_QUALITY_REMOTE, (String)items[which]);
        dialog.dismiss();
      }
    });
    builder.create().show();
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
      Logger.d("Exception getting google search version: " + e.getStackTrace());
    }
    return false;
  }

  protected void setCastIconInactive() {
    Logger.d("[NewMainActivity] setCastIconInactive");
    try {
      castIconMenuItem.setIcon(R.drawable.mr_ic_media_route_holo_dark);
    } catch (Exception e) {}
  }

  protected void setCastIconActive() {
    Logger.d("[NewMainActivity] setCastIconActive");
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
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    // TODO: Restore instance state?
    client = (PlexClient)savedInstanceState.getParcelable(com.atomjack.shared.Intent.EXTRA_CLIENT);
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

  /*
  @Override
  public void onSubscribed() {
    Logger.d("[NewMainActivity] onSubscribed: %s", client);


  }

  @Override
  public void onUnsubscribed() {

  }

  @Override
  public void onStopped() {

  }

  @Override
  public void onFoundPlayingMedia(final Timeline timeline) {
    Logger.d("[NewMainActivity] found key: %s", timeline.key);


  }
  */

  private DrawerLayout.DrawerListener drawerListener = new DrawerLayout.DrawerListener() {
    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {

    }

    @Override
    public void onDrawerOpened(View drawerView) {

    }

    @Override
    public void onDrawerClosed(View drawerView) {
      setNavGroup(R.menu.nav_items_main);
    }

    @Override
    public void onDrawerStateChanged(int newState) {

    }
  };

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
}
