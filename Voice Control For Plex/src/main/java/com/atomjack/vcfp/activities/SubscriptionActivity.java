package com.atomjack.vcfp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexSubscription;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexClient;

public class SubscriptionActivity extends VCFPActivity {
  public static final String ACTION_SUBSCRIBE = "com.atomjack.vcfp.action_subscribe";
  public static final String ACTION_UNSUBSCRIBE = "com.atomjack.vcfp.action_unsubscribe";

  public static final String CLIENT = "com.atomjack.vcfp.client";

  private PlexSubscription plexSubscription;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Logger.d("SubscriptionActivity onCreate");

    plexSubscription = VoiceControlForPlexApplication.getInstance().plexSubscription;
    handleIntent(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Logger.d("SubscriptionActivity onNewIntent");
    handleIntent(intent);
  }

  private void handleIntent(Intent intent) {
    PlexClient client = null;
    if(intent.getAction().equals(ACTION_SUBSCRIBE)) {
      client = getIntent().getParcelableExtra(CLIENT);
      if (client != null)
        connectTo(client);
      else {
        Logger.d("Client not found");
        finish();
      }
    } else if(intent.getAction().equals(ACTION_UNSUBSCRIBE)) {
      plexSubscription.setListener(this);
      plexSubscription.unsubscribe();
      mNotifyMgr.cancel(mNotificationId);
      feedback.m(R.string.disconnected, new Runnable() {
        @Override
        public void run() {
          delayedFinish();
        }
      });
    } else
      finish();
  }

  private void connectTo(PlexClient client) {
    plexSubscription.setListener(this);
    plexSubscription.subscribe(client);
    feedback.m(String.format(getString(R.string.connected_to2), client.name), new Runnable() {
      @Override
      public void run() {
        delayedFinish();
      }
    });
  }

  // Delay 2.1 seconds to let the toast show and disappear before finishing this activity.
  private void delayedFinish() {
    new Handler().postDelayed(new Runnable() {
      @Override
      public void run() {
        finish();
      }
    }, 2100);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    feedback.destroy();
  }

  @Override
  public void onSubscribed(PlexClient _client) {
    mClient = _client;
    // Do nothing
  }

  @Override
  public void onUnsubscribed() {
    // Do nothing
  }
}
