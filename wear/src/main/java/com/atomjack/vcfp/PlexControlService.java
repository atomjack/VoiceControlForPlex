package com.atomjack.vcfp;

import android.app.IntentService;
import android.content.Intent;

import com.atomjack.shared.Logger;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.WearConstants;

public class PlexControlService extends IntentService {


  public PlexControlService() {
    super("Plex Control Service");
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    String action = intent.getAction();
    if(action != null) {
      Logger.d("action: %s", action);
      new SendToDataLayerThread(action, this).start();
      if(action.equals(WearConstants.ACTION_PLAY)) {
        Logger.d("play!");
      }





    }




  }
}
