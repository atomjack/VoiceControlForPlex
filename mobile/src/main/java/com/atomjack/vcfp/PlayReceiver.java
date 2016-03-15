package com.atomjack.vcfp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atomjack.shared.Logger;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.services.PlexSearchService;

public class PlayReceiver extends BroadcastReceiver
{
  @Override
  public void onReceive(final Context context, final Intent intent)
  {
    Logger.d("PLAYRECEIVER");
    Bundle bundle = intent.getExtras();
    String queryText = bundle.getString(com.atomjack.shared.Intent.ARGUMENTS);
    if(queryText == null && intent.getStringExtra(GoogleSearchApi.KEY_QUERY_TEXT) != null)
      queryText = intent.getStringExtra(GoogleSearchApi.KEY_QUERY_TEXT);

    if(queryText != null && queryText.matches(context.getResources().getString(R.string.pattern_recognition))) {
      queryText = queryText.toLowerCase();
      Intent sendIntent = new Intent(context, PlexSearchService.class);
      sendIntent.putExtra(com.atomjack.shared.Intent.EXTRA_QUERYTEXT, queryText);
      sendIntent.putExtra(com.atomjack.shared.Intent.EXTRA_RESUME, VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false));
      sendIntent.putExtra(com.atomjack.shared.Intent.EXTRA_FROM_GOOGLE_NOW, true);
      sendIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
      sendIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
      sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startService(sendIntent);
    }
  }
}
