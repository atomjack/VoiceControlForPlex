package com.atomjack.vcfp.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import com.atomjack.vcfp.services.LocalMusicService;

public class RemoteControlReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
      KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
      Intent sendIntent = new Intent(context, LocalMusicService.class);
      sendIntent.setAction(com.atomjack.shared.Intent.ACTION_MEDIA_BUTTON);
      sendIntent.putExtra(com.atomjack.shared.Intent.KEY_EVENT, event);
      context.startService(sendIntent);

      if(isOrderedBroadcast())
        abortBroadcast();
    }
  }
}

