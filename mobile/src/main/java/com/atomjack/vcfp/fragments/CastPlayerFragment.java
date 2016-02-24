package com.atomjack.vcfp.fragments;

import android.widget.SeekBar;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.vcfp.CastPlayerManager;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexMedia;

public class CastPlayerFragment extends PlayerFragment {
  private CastPlayerManager castPlayerManager;

  public CastPlayerFragment() {
    super();
    castPlayerManager = VoiceControlForPlexApplication.getInstance().castPlayerManager;
  }

  @Override
  public PlexMedia getNowPlayingMedia() {
    return nowPlayingMedia;
  }

  @Override
  protected void doRewind() {
    if(position > -1) {
      nowPlayingMedia.viewOffset = Integer.toString((position * 1000) - 15000);
      if (Integer.parseInt(nowPlayingMedia.viewOffset) < 0) {
        position = 0;
        nowPlayingMedia.viewOffset = "0";
      }
      castPlayerManager.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
    }
  }

  @Override
  protected void doForward() {
    Logger.d("Doing forward, position: %d", position);
    if(position > -1) {
      nowPlayingMedia.viewOffset = Integer.toString((position * 1000) + 30000);
      castPlayerManager.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
    }
  }

  @Override
  protected void doPlayPause() {
    try {
      Logger.d("doPlayPause, currentState: %s", currentState);
      if(currentState !=  PlayerState.PLAYING) {
        castPlayerManager.play();
      } else if(currentState ==  PlayerState.PLAYING) {
        castPlayerManager.pause();
      }
    } catch (Exception ex) {}
  }

  @Override
  protected void doStop() {
    try {
      castPlayerManager.stop();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  @Override
  protected void doNext() {
    castPlayerManager.doNext();
  }

  @Override
  protected void doPrevious() {
    castPlayerManager.doPrevious();
  }

  @Override
  public void onStopTrackingTouch(SeekBar seekBar) {
    Logger.d("stopped changing progress: %d", seekBar.getProgress());
    try {
      nowPlayingMedia.viewOffset = Integer.toString(seekBar.getProgress() * 1000);
      castPlayerManager.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    isSeeking = false;
  }
}
