package com.atomjack.vcfp.interfaces;

import com.atomjack.shared.PlayerState;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;

import java.util.ArrayList;

public interface PlexSubscriptionListener {
  void onSubscribed(PlexClient client);
  void onUnsubscribed();
  void onTimeUpdate(PlayerState state, int seconds);
  void onMediaChanged(PlexMedia media, PlayerState state);
  void onStateChanged(PlexMedia media, PlayerState state);
  void onPlayStarted(PlexMedia media, ArrayList<? extends PlexMedia> playlist, PlayerState state);
  void onSubscribeError(String message);
}
