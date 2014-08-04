package com.atomjack.vcfp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkChangeReceiver extends BroadcastReceiver {
  private boolean wifiConnected;

  @Override
  public void onReceive(Context context, Intent intent) {

    if(intent.getAction() != null && intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {

      ConnectivityManager cm =
              (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

      NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

      Logger.d("Current type: %d", activeNetwork.getType());
      int type = activeNetwork.getType();
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
    }
    /*

    if(lastType != activeNetwork.getType()) {
      Logger.d("LastType: %d", lastType);
      lastType = activeNetwork.getType();
      boolean isWiFi = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI || activeNetwork.getType() == ConnectivityManager.TYPE_ETHERNET;



      Logger.d("isWiFi: %s", isWiFi);
    }
    */
    /*
    if(intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
      NetworkInfo networkInfo =
              intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
      if(networkInfo.isConnected()) {
        // Wifi is connected
        Logger.d("Wifi is connected: " + String.valueOf(networkInfo));
      }
    } else if(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
      ConnectivityManager cm =
              (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

      NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
      Logger.d("Type now: %d", activeNetwork.getType());
//      NetworkInfo networkInfo =
//              intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
      if(activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
        // Wifi is disconnected
        Logger.d("Wifi is disconnected: " + String.valueOf(activeNetwork));
      }
    }
    */




  }
}
