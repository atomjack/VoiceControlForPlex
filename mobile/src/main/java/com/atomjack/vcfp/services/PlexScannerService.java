package com.atomjack.vcfp.services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.IBinder;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.atomjack.shared.Logger;
import com.atomjack.shared.NewLogger;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.interfaces.ActiveConnectionHandler;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.Device;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.net.PlexHttpClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import retrofit.Call;
import retrofit.Callback;
import retrofit.Response;
import retrofit.Retrofit;
import us.nineworlds.serenity.GDMReceiver;

public class PlexScannerService extends Service {
  public static final String ACTION_SCAN_SERVERS = "com.atomjack.vcfp.plexscannerservice.action_scan_servers";
  public static final String ACTION_SCAN_CLIENTS = "com.atomjack.vcfp.plexscannerservice.action_scan_clients";
  public static final String ACTION_SERVER_SCAN_FINISHED = "com.atomjack.vcfp.plexscannerservice.action_server_scan_finished";
  public static final String REMOTE_SERVER_SCAN_UNAUTHORIZED = "com.atomjack.vcfp.plexscannerservice.remote_server_scan_unauthorized";
//  public static final String ACTION_LOCAL_SERVER_SCAN_FINISHED = "com.atomjack.vcfp.plexscannerservice.action_local_server_scan_finished";
  public static final String ACTION_CLIENT_SCAN_FINISHED = "com.atomjack.vcfp.plexscannerservice.action_client_scan_finished";
  public static final String SCAN_TYPE = "com.atomjack.vcfp.plexscannerservice.scan_type";

  // The class that is doing a scan. This class will have an intent sent back to it when scanning is done.
  public static final String CLASS = "com.atomjack.vcfp.plexscannerservice.class";
  public static final String CONNECT_TO_CLIENT = "com.atomjack.vcfp.plexscannerservice.connect_to_client";
  public static final String CANCEL = "com.atomjack.vcfp.plexscannerservice.cancel";

  private NewLogger logger;

  private Class callingClass;

  private BroadcastReceiver gdmReceiver;

  private boolean localServerScanFinished = false;
  private boolean remoteServerScanFinished = false;

  private static boolean cancel = false;

  private ConcurrentHashMap<String, PlexServer> servers = new ConcurrentHashMap<String, PlexServer>();
  private List<PlexClient> clients = new ArrayList<>();

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    String action = intent.getAction();
    if(action != null) {
      Logger.d("[PlexScannerService] Action: %s", action);
      if(action.equals(ACTION_SCAN_SERVERS)) {
        remoteServerScanFinished = false;
        localServerScanFinished = false;
        cancel = false;
        servers = new ConcurrentHashMap<>();
        setClass(intent);
        scanForServers();
      } else if(action.equals(ACTION_SCAN_CLIENTS)) {
        setClass(intent);
        scanForClients(intent.getBooleanExtra(com.atomjack.shared.Intent.EXTRA_CONNECT_TO_CLIENT, false));
      } else if(action.equals(ACTION_SERVER_SCAN_FINISHED)) {
        List<PlexServer> localServers = intent.getParcelableArrayListExtra(com.atomjack.shared.Intent.EXTRA_SERVERS);
        Logger.d("local server scan finished with %d servers", localServers.size());
        Logger.d("is logged in: %s", VoiceControlForPlexApplication.getInstance().isLoggedIn());
        Logger.d("remoteServerScanFinished: %s", remoteServerScanFinished);
        localServerScanFinished = true;
        for(PlexServer ls : localServers) {
          if(!servers.containsKey(ls.machineIdentifier))
            servers.put(ls.machineIdentifier, ls);
        }
        if(remoteServerScanFinished || !VoiceControlForPlexApplication.getInstance().isLoggedIn()) {
          onServerScanFinished();
        }
      } else if(action.equals(ACTION_CLIENT_SCAN_FINISHED)) {
        clients = intent.getParcelableArrayListExtra(com.atomjack.shared.Intent.EXTRA_CLIENTS);
        onClientScanFinished();
      } else if(action.equals(CANCEL)) {
        cancel = true;
      }
    }
    return Service.START_NOT_STICKY;
  }

  private void setClass(Intent intent) {
    callingClass = (Class) intent.getSerializableExtra(CLASS);
  }

  private void onServerScanFinished() {
    onScanFinished(ACTION_SERVER_SCAN_FINISHED);
  }

  private void onClientScanFinished() {
    onScanFinished(ACTION_CLIENT_SCAN_FINISHED);
  }

  private void onScanFinished(String type) {
    onScanFinished(type, false);
  }
  private void onScanFinished(String type, final boolean foundUnauthorized) {
    if(cancel)
      return;

    if(type == ACTION_SERVER_SCAN_FINISHED) {
      final int[] serversScanned = new int[1];
      serversScanned[0] = 0;
      final int numServers = servers.size();
      for (final PlexServer server : servers.values()) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            server.findServerConnection(true, new ActiveConnectionHandler() {
              @Override
              public void onSuccess(Connection connection) {
                Logger.d("Found active connection for %s: %s", server.name, connection);
                serversScanned[0]++;
                PlexHttpClient.PlexHttpService service = PlexHttpClient.getService(connection);
                Call<MediaContainer> call = service.getLibrarySections(server.accessToken);
                call.enqueue(new Callback<MediaContainer>() {
                  @Override
                  public void onResponse(Response<MediaContainer> response, Retrofit retrofit) {
                    MediaContainer mc = response.body();
                    server.movieSections = new ArrayList<>();
                    server.tvSections = new ArrayList<>();
                    server.musicSections = new ArrayList<>();
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
                    if(!server.name.equals("")) {
                      // Finally, if this server is the current default server, save it in preferences so the access token gets transferred
                      PlexServer defaultServer = VoiceControlForPlexApplication.gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SERVER, ""), PlexServer.class);
                      if(defaultServer != null && server.machineIdentifier.equals(defaultServer.machineIdentifier)) {
                        VoiceControlForPlexApplication.getInstance().prefs.put(Preferences.SERVER, VoiceControlForPlexApplication.gsonWrite.toJson(server));
                      }
                      Logger.d("Added %s.", server.name);
                    }
                    // since we're finding server connections concurrently, in separate threads, we have to wait until all servers are done being scanned.
                    if (serversScanned[0] >= numServers) {
                      sendServerScanFinishedIntent(foundUnauthorized);
                    }
                  }

                  @Override
                  public void onFailure(Throwable t) {
                    if (serversScanned[0] >= numServers) {
                      sendServerScanFinishedIntent(foundUnauthorized);
                    }
                  }
                });
              }

              @Override
              public void onFailure(int statusCode) {
                Logger.d("Couldn't find active connection for %s", server.name);
                // Remove this server from the servers we found
                servers.remove(server.machineIdentifier);
                serversScanned[0]++;
                if (serversScanned[0] >= numServers) {
                  sendServerScanFinishedIntent(foundUnauthorized);
                }
              }
            });


            return null;
          }
        }.execute();

      }
    } else if(type == ACTION_CLIENT_SCAN_FINISHED) {
      Intent intent = new Intent(this, callingClass);
      intent.setAction(type);
      intent.putExtra(com.atomjack.shared.Intent.EXTRA_CLIENTS, (ArrayList<PlexClient>)clients);
      intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
      intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if (callingClass.getSuperclass() == Service.class)
        startService(intent);
      else
        startActivity(intent);
    }
  }

  private void sendServerScanFinishedIntent(boolean foundUnauthorized) {
    Intent intent = new Intent(this, callingClass);
    Logger.d("[PlexScannerService] onScanFinished, have %d servers", servers.size());
    HashMap<String, PlexServer> s = new HashMap<>();
    for(PlexServer server : servers.values())
      s.put(server.name, server);
    intent.putExtra(com.atomjack.shared.Intent.EXTRA_SERVERS, s);
    intent.setAction(ACTION_SERVER_SCAN_FINISHED);
    intent.putExtra(REMOTE_SERVER_SCAN_UNAUTHORIZED, foundUnauthorized);
    intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (callingClass.getSuperclass() == Service.class)
      startService(intent);
    else
      startActivity(intent);
  }

  private void scanForServers() {
    VoiceControlForPlexApplication.getInstance().unauthorizedLocalServersFound.clear();
    if(VoiceControlForPlexApplication.getInstance().isLoggedIn()) {
      refreshResources(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.AUTHENTICATION_TOKEN), new RefreshResourcesResponseHandler() {
        @Override
        public void onSuccess() {
          remoteServerScanFinished = true;
          if (localServerScanFinished)
            onServerScanFinished();
        }

        @Override
        public void onFailure(int statusCode) {
          Logger.d("[PlexScannerService] failure: %d", statusCode);
          if (statusCode == 401) { // Unauthorized
            onScanFinished(ACTION_SERVER_SCAN_FINISHED, true);
          }
        }
      });
    }
    Logger.d("Doing local scan for servers");
    Intent mServiceIntent = new Intent(this, GDMService.class);
    mServiceIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//    mServiceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_SILENT, silent);
    mServiceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_CLASS, PlexScannerService.class);
    mServiceIntent.putExtra(com.atomjack.shared.Intent.SCAN_TYPE, com.atomjack.shared.Intent.SCAN_TYPE_SERVER);
    startService(mServiceIntent);

  }

  public void scanForClients() {
    scanForClients(false);
  }

  private void scanForClients(boolean connectToClient) {
    Intent mServiceIntent = new Intent(this, GDMService.class);
    mServiceIntent.putExtra(GDMService.PORT, 32412); // Port for clients
    mServiceIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    mServiceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_CLASS, PlexScannerService.class);
    mServiceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_CONNECT_TO_CLIENT, connectToClient);
    mServiceIntent.putExtra(com.atomjack.shared.Intent.SCAN_TYPE, com.atomjack.shared.Intent.SCAN_TYPE_CLIENT);
    startService(mServiceIntent);
    VoiceControlForPlexApplication.hasDoneClientScan = true;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    logger = new NewLogger(this);
    gdmReceiver = new GDMReceiver();
    IntentFilter filters = new IntentFilter();
    filters.addAction(GDMService.MSG_RECEIVED);
    filters.addAction(GDMService.SOCKET_CLOSED);
    filters.addAction(GDMReceiver.ACTION_CANCEL);
    LocalBroadcastManager.getInstance(this).registerReceiver(gdmReceiver,
            filters);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if(gdmReceiver != null) {
      LocalBroadcastManager.getInstance(this).unregisterReceiver(gdmReceiver);
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  // Remote Scan methods
  public interface RefreshResourcesResponseHandler {
    void onSuccess();
    void onFailure(int statusCode);
  }

  public void refreshResources(String authToken, final RefreshResourcesResponseHandler responseHandler) {
    refreshResources(authToken, responseHandler, false);
  }

  public void refreshResources(String authToken) {
    refreshResources(authToken, null, true);
  }

  public void refreshResources(String authToken, final RefreshResourcesResponseHandler responseHandler, boolean silent) {
    Logger.d("Fetching resources from plex.tv");
    cancel = false;
    VoiceControlForPlexApplication.hasDoneClientScan = true;
    PlexHttpClient.PlexHttpService service = PlexHttpClient.getService("https://plex.tv");
    Call<MediaContainer> call = service.getResources(authToken);
    call.enqueue(new Callback<MediaContainer>() {
      @Override
      public void onResponse(Response<MediaContainer> response, Retrofit retrofit) {
        try {
          if(cancel) {
            cancel = false;
            return;
          }
          MediaContainer mediaContainer = response.body();

          Logger.d("got %d devices", mediaContainer.devices.size());

//          List<PlexServer> servers = new ArrayList<PlexServer>();
          for(final Device device : mediaContainer.devices) {
            if(device.lastSeenAt < System.currentTimeMillis()/1000 - (60*60*24))
              continue;
            if(device.provides.contains("server")) {

              PlexServer server = PlexServer.fromDevice(device);
              Logger.d("Device %s is a server, has %d connections (owned: %s)", server.name, server.connections.size(), server.owned);
              if(!servers.containsKey(server.machineIdentifier))
                servers.put(server.machineIdentifier, server);
              if(VoiceControlForPlexApplication.getInstance().unauthorizedLocalServersFound.contains(server.machineIdentifier))
                VoiceControlForPlexApplication.getInstance().unauthorizedLocalServersFound.remove(server.machineIdentifier);
            } else if(device.provides.contains("player")) {
              Logger.d("Device %s is a player", device.name);
            }
          }

          responseHandler.onSuccess();

          /*
          final int[] serversScanned = new int[1];
          serversScanned[0] = 0;
          final int numServers = servers.size();
          for(final PlexServer server : servers) {
            VoiceControlForPlexApplication.addPlexServer(server, new Runnable() {
              @Override
              public void run() {
                Logger.d("Done scanning %s", server.name);
                serversScanned[0]++;
                Logger.d("%d out of %d servers scanned", serversScanned[0], numServers);
                if(serversScanned[0] >= numServers && responseHandler != null)
                  responseHandler.onSuccess();
              }
            });
          }
          */
        } catch (Exception e) {
          if(responseHandler != null)
            responseHandler.onFailure(0);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        Logger.d("Failure getting resources.");
        t.printStackTrace();
        if(cancel) {
          cancel = false;
          return;
        }
        if(responseHandler != null)
          responseHandler.onFailure(0);
      }
    });
  }

}
