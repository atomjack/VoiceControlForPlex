package com.atomjack.vcfp.fragments;

import android.view.View;

import com.atomjack.shared.PlayerState;
import com.atomjack.vcfp.CastPlayerManager;
import com.atomjack.vcfp.model.Capabilities;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;

public class CastPlayerFragment extends PlayerFragment implements CastPlayerManager.CastListener {
  public CastPlayerFragment() {
    super();

  }

  @Override
  public void onCastConnected(PlexClient client) {

  }

  @Override
  public void onCastDisconnected() {

  }

  @Override
  public void onCastPlayerStateChanged(PlayerState state) {

  }

  @Override
  public void onCastPlayerTimeUpdate(int seconds) {
    // This is the equivalent to onTimelineReceived
  }

  @Override
  public void onCastPlayerPlaylistAdvance(PlexMedia media) {

  }

  @Override
  public void onCastPlayerState(PlayerState state, PlexMedia media) {

  }

  @Override
  public void onCastConnectionFailed() {

  }

  @Override
  public void onCastSeek() {

  }

  @Override
  public void onGetDeviceCapabilities(Capabilities capabilities) {

  }

  @Override
  public PlexMedia getNowPlayingMedia() {
    return null;
  }

  @Override
  protected void doRewind() {

  }

  @Override
  protected void doForward() {

  }

  @Override
  protected void doPlayPause() {

  }

  @Override
  protected void doStop() {

  }

  @Override
  protected void doNext() {

  }

  @Override
  protected void doPrevious() {

  }

  @Override
  protected void doMediaOptions() {

  }

  @Override
  protected void doMic() {

  }
}
