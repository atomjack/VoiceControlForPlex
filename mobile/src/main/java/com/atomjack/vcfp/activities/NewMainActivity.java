package com.atomjack.vcfp.activities;

import android.Manifest;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.vending.billing.IabHelper;
import com.android.vending.billing.IabResult;
import com.android.vending.billing.Purchase;
import com.atomjack.shared.Logger;
import com.atomjack.shared.Preferences;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.UriDeserializer;
import com.atomjack.shared.UriSerializer;
import com.atomjack.shared.WearConstants;
import com.atomjack.vcfp.BuildConfig;
import com.atomjack.vcfp.Feedback;
import com.atomjack.vcfp.FutureRunnable;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.Utils;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.adapters.PlexListAdapter;
import com.atomjack.vcfp.fragments.MainFragment;
import com.atomjack.vcfp.model.Pin;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexUser;
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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NewMainActivity extends AppCompatActivity {

  public final static int RESULT_VOICE_FEEDBACK_SELECTED = 0;
  public final static int RESULT_TASKER_PROJECT_IMPORTED = 1;
  public final static int RESULT_SHORTCUT_CREATED = 2;

  private final static int SERVER_SCAN_INTERVAL = 1000*60*5; // scan for servers every 5 minutes
  private Handler handler;

  private DrawerLayout mDrawer;
  private Toolbar toolbar;
  private NavigationView nvDrawer;
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

  protected Feedback feedback;

  MediaRouter mMediaRouter;
  MediaRouterCallback mMediaRouterCallback;
  MediaRouteSelector mMediaRouteSelector;

  protected Dialog deviceSelectDialog = null;

  protected PlexClient postChromecastPurchaseClient = null;
  protected Runnable postChromecastPurchaseAction = null;

  protected Gson gsonWrite = new GsonBuilder()
          .registerTypeAdapter(Uri.class, new UriSerializer())
          .create();
  protected Gson gsonRead = new GsonBuilder()
          .registerTypeAdapter(Uri.class, new UriDeserializer())
          .create();

  private boolean userIsInteracting;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.new_activity_main);

    handler = new Handler();

    Fragment mainFragment = new MainFragment();


    FragmentManager fragmentManager = getSupportFragmentManager();
    fragmentManager.beginTransaction().replace(R.id.flContent, mainFragment).commit();

    final WhatsNewDialog whatsNewDialog = new WhatsNewDialog(this);
    whatsNewDialog.show();

    authToken = VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.AUTHENTICATION_TOKEN);



    feedback = new Feedback(this);

    mMediaRouter = MediaRouter.getInstance(getApplicationContext());
    mMediaRouteSelector = new MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(BuildConfig.CHROMECAST_APP_ID))
            .build();
    mMediaRouterCallback = new MediaRouterCallback();
    mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);

    server = gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SERVER, ""), PlexServer.class);
    if(server == null)
      server = new PlexServer(getString(R.string.scan_all));

    client = gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.CLIENT, ""), PlexClient.class);


    setupNavigationDrawer();
  }

  @Override
  protected void onPause() {
    super.onPause();
    Logger.d("[NewMainActivity] onPause");
    handler.removeCallbacks(refreshServers);
  }

  @Override
  protected void onResume() {
    super.onResume();
    Logger.d("[NewMainActivity] onResume");
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
    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(NewMainActivity.this);
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
    final Context context = NewMainActivity.this;
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
                  setupNavigationDrawer();
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

  private void showManualLogin() {
    LayoutInflater layoutInflater = LayoutInflater.from(NewMainActivity.this);
    View promptView = layoutInflater.inflate(R.layout.login, null);
    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(NewMainActivity.this);
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
            // TODO: more changes due to login here
//            MenuItem loginItem = menu.findItem(R.id.menu_login);
//            loginItem.setVisible(false);
//            MenuItem logoutItem = menu.findItem(R.id.menu_logout);
//            logoutItem.setVisible(true);
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

//    nvDrawer.getMenu().setGroupVisible(R.id.drawer_group_logout, false);

    // TODO: If the currently selected server is not local, reset it to scan all. (MainActivity:541)

    // Remove any non-local servers from our list
    for(PlexServer s : VoiceControlForPlexApplication.servers.values()) {
      if(!s.local)
        VoiceControlForPlexApplication.servers.remove(s.name);
    }

    VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SAVED_SERVERS, gsonWrite.toJson(VoiceControlForPlexApplication.servers));

    // Refresh the navigation drawer
    setupNavigationDrawer();

    feedback.m(R.string.logged_out);
  }

  private void setupNavigationDrawer() {
    // Set a Toolbar to replace the ActionBar.
    toolbar = (Toolbar) findViewById(R.id.nav_toolbar);
    setSupportActionBar(toolbar);

    // Find our drawer view
    mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
    mDrawer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    drawerToggle = new ActionBarDrawerToggle(this, mDrawer, toolbar, R.string.drawer_open, R.string.drawer_close);

    // Find our drawer view
    nvDrawer = (NavigationView) findViewById(R.id.nvView);
    nvDrawer.getMenu().clear();
    nvDrawer.inflateMenu(R.menu.nav_items_main);

    if(nvDrawer.getHeaderView(0) != null)
      nvDrawer.removeHeaderView(nvDrawer.getHeaderView(0));
    // Setup drawer view
    setupDrawerContent(nvDrawer);


    // Kick off a scan for servers, if it's been more than five minutes since the last one.
    // We'll later want to do this every five minutes or so, to keep the list up to date
    Date now = new Date();
    Date lastServerScan = new Date(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.LAST_SERVER_SCAN, 0l));
    Logger.d("now: %s", now);
    Logger.d("lastServeRscan: %s", lastServerScan);
    if(now.after(Utils.addMinutesToDate(5, lastServerScan))) {
      refreshServers.run();
    } else
      handler.postDelayed(refreshServers, SERVER_SCAN_INTERVAL);

    // Fill in the list of servers we have now. This will be called again each time we receive an intent from the Scanner Service
    refreshNavServers();

    if(authToken != null) {
      nvDrawer.inflateHeaderView(R.layout.nav_header_logged_in);
      if(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_EMAIL) != null) {
        setUserThumb();
        Logger.d("Username = %s", VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME));
        final View navHeader = nvDrawer.getHeaderView(0);

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
            AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(NewMainActivity.this, flip);
            set.setTarget(image);
            set.start();
          }
        });
      }
    } else {
      nvDrawer.getMenu().findItem(R.id.nav_login).setVisible(true);
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Logger.d("NewMainActivity onNewIntent: %s", intent.getAction());

    if(intent.getAction().equals(PlexScannerService.ACTION_SERVER_SCAN_FINISHED)) {
      // Refresh the list of servers in the navigation drawer
      refreshNavServers();
    }
  }

  private Runnable refreshServers = new Runnable() {
    @Override
    public void run() {
      Intent scannerIntent = new Intent(NewMainActivity.this, PlexScannerService.class);
      scannerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      scannerIntent.putExtra(PlexScannerService.CLASS, NewMainActivity.class);
      scannerIntent.setAction(PlexScannerService.ACTION_SCAN_SERVERS);
      startService(scannerIntent);
      VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.LAST_SERVER_SCAN, new Date().getTime());
      handler.postDelayed(refreshServers, SERVER_SCAN_INTERVAL);
    }
  };

  public void refreshNavServers() {
    if(nvDrawer.getMenu().findItem(R.id.nav_server) != null) {
      Spinner serverSpinner = (Spinner) nvDrawer.getMenu().findItem(R.id.nav_server).getActionView();
      PlexListAdapter adapter = new PlexListAdapter(this, PlexListAdapter.TYPE_SERVER);
      adapter.setServers(VoiceControlForPlexApplication.servers);
      Logger.d("Found %d servers", VoiceControlForPlexApplication.servers.size());
      serverSpinner.setAdapter(null);
      serverSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
          if(userIsInteracting) {
            PlexServer s = (PlexServer) parent.getItemAtPosition(position);
            Logger.d("selected %s", s.name);
            server = s;
            saveSettings();

          }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
      });
      serverSpinner.setAdapter(adapter);
      if(server != null) {
        serverSpinner.setSelection(adapter.getServerIndex(server));
      }
    }
  }

  public void navMenuSettingsBack(MenuItem item) {
    setNavGroup(R.menu.nav_items_main);
  }

  public void navMenuSettings(MenuItem item) {
    setNavGroup(R.menu.nav_items_settings);
  }

  private void setNavGroup(int group) {
    nvDrawer.getMenu().clear();
    nvDrawer.inflateMenu(group);
    if(group == R.menu.menu_main) {
      nvDrawer.getMenu().findItem(R.id.nav_login).setVisible(authToken == null);
    } else if(group == R.menu.nav_items_main) {
      refreshNavServers();
    }
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
          View navHeader = nvDrawer.getHeaderView(0);
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
    return super.onCreateOptionsMenu(menu);
  }

  private void setupDrawerContent(NavigationView navigationView) {
    navigationView.setNavigationItemSelectedListener(
            new NavigationView.OnNavigationItemSelectedListener() {
              @Override
              public boolean onNavigationItemSelected(MenuItem menuItem) {
                Logger.d("got here: %d", menuItem.getItemId());
//                selectDrawerItem(menuItem);
                return true;
              }
            });
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
    }

    return super.onOptionsItemSelected(item);
  }

  // Make sure this is the method with just `Bundle` as the signature
  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
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
      if(VoiceControlForPlexApplication.castClients.containsKey(route.getName())) {
        Logger.d("Cast Client %s has gone missing. Removing.", route.getName());
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

  public void showAbout(MenuItem item) {
    item.setChecked(false);
    AlertDialog.Builder alertDialog = new AlertDialog.Builder(NewMainActivity.this)
            .setTitle(R.string.app_name)
            .setMessage(R.string.about_text);

    alertDialog.show();
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
          boolean hasPermission = (ContextCompat.checkSelfPermission(NewMainActivity.this,
                  Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
          if(!hasPermission) {
            ActivityCompat.requestPermissions(NewMainActivity.this,
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
    new AlertDialog.Builder(NewMainActivity.this)
            .setMessage(String.format(getString(stringResource), VoiceControlForPlexApplication.getWearPrice()))
            .setCancelable(false)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.HAS_SHOWN_WEAR_PURCHASE_POPUP, true);
                dialogInterface.cancel();
                VoiceControlForPlexApplication.getInstance().getIabHelper().launchPurchaseFlow(NewMainActivity.this,
                        VoiceControlForPlexApplication.SKU_WEAR, 10001, mPurchaseFinishedListener,
                        VoiceControlForPlexApplication.SKU_TEST_PURCHASED == VoiceControlForPlexApplication.SKU_WEAR ? VoiceControlForPlexApplication.getInstance().getEmailHash() : "");
              }
            })
            .setNeutralButton(R.string.no_thanks, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.HAS_SHOWN_WEAR_PURCHASE_POPUP, true);
                if(showPurchaseFromMenu) {
                  new AlertDialog.Builder(NewMainActivity.this)
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
        new SendToDataLayerThread(WearConstants.WEAR_PURCHASED, NewMainActivity.this).start();
      }
    }
  };

  protected void hidePurchaseWearMenuItem() {
  }

  public void showWearOptions(MenuItem item) {
    AlertDialog.Builder chooserDialog = new AlertDialog.Builder(this);
    chooserDialog.setTitle(R.string.wear_primary_function);
    chooserDialog.setMessage(R.string.wear_primary_function_option_description);
    chooserDialog.setPositiveButton(R.string.voice_input, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        DataMap dataMap = new DataMap();
        dataMap.putBoolean(WearConstants.PRIMARY_FUNCTION_VOICE_INPUT, true);
        new SendToDataLayerThread(WearConstants.SET_WEAR_OPTIONS, dataMap, NewMainActivity.this).start();
        dialog.dismiss();
      }
    });
    chooserDialog.setNeutralButton(R.string.play_pause, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        DataMap dataMap = new DataMap();
        dataMap.putBoolean(WearConstants.PRIMARY_FUNCTION_VOICE_INPUT, false);
        new SendToDataLayerThread(WearConstants.SET_WEAR_OPTIONS, dataMap, NewMainActivity.this).start();
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
    Logger.d("user is interacting");
    userIsInteracting = true;
  }
}
