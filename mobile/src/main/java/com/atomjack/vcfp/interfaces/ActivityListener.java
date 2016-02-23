package com.atomjack.vcfp.interfaces;

// This interface is used for the PlayerFragment to pass messages on to the activity
public interface ActivityListener {
  void onLayoutNotFound();
  // This will get passed to the activity with the key of the currently playing media, so the activity can set up the
  // fragment to display the media
//  void onFoundPlayingMedia(Timeline timeline);
  // Need this so that the activity can set the cast icon as active
//  void onSubscribed();
//  void onUnsubscribed();
//  void onStopped();
//  void onStopped();
}