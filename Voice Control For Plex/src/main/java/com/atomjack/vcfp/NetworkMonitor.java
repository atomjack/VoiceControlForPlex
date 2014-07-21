package com.atomjack.vcfp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkMonitor {
  private boolean connected;
  private int currentState;
  private Context mContext;

  public NetworkMonitor(Context context) {
    mContext = context;
    register();
  }

  public void unregister() {
    try {
      mContext.unregisterReceiver(networkChangeReceiver);
    } catch (Exception e) {}
  }

  public void register() {
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    mContext.registerReceiver(networkChangeReceiver, intentFilter);
  }

  private BroadcastReceiver networkChangeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if(intent.getAction() != null && intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {

        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        if(activeNetwork == null) {
          if(connected != false) {
            connected = false;
            VoiceControlForPlexApplication.getInstance().onNetworkDisconnected();
          }
        } else {
          connected = true;
          int type = activeNetwork.getType();
          if (type != currentState) {
            currentState = type;
            VoiceControlForPlexApplication.getInstance().onNetworkConnected(currentState);
          }
        }

        /*
        if (type == ConnectivityManager.TYPE_WIFI) {
          if (!wifiConnected) {
            wifiConnected = true;
            Logger.d("Wifi Connected");
          }
        } else if (type == ConnectivityManager.TYPE_MOBILE) {
          if (wifiConnected) {
            wifiConnected = false;
            Logger.d("Wifi Disconnected");
          }
        }
        */
      }
    }
  };
}
