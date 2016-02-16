package com.atomjack.vcfp.interfaces;

import com.atomjack.shared.PlayerState;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;

public interface PlexSubscriptionListener {
  void onSubscribed(PlexClient client);
  void onUnsubscribed();
  void onTimeUpdate(int seconds);
  void onMediaChanged(PlexMedia media);
  void onStateChanged(PlayerState state);
  void onPlayStarted(PlexMedia media, PlayerState state);
//  void onTimelineReceived(MediaContainer mediaContainer); // Will probably have to be changed to match with cast clients
  void onSubscribeError(String message);
}
