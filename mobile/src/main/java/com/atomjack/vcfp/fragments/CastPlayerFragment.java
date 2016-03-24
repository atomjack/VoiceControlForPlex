package com.atomjack.vcfp.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.SeekBar;

import com.atomjack.vcfp.CastPlayerManager;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexMedia;

import butterknife.ButterKnife;

public class CastPlayerFragment extends PlayerFragment {
  private CastPlayerManager castPlayerManager;

  public CastPlayerFragment() {
    super();
    castPlayerManager = VoiceControlForPlexApplication.getInstance().castPlayerManager;
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    ButterKnife.bind(this, view);
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
    logger.d("Doing forward, position: %d", position);
    if(position > -1) {
      nowPlayingMedia.viewOffset = Integer.toString((position * 1000) + 30000);
      castPlayerManager.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
    }
  }

  @Override
  protected void doPlay() {
    castPlayerManager.play();
  }

  @Override
  protected void doPause() {
    castPlayerManager.pause();
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
    logger.d("stopped changing progress: %d", seekBar.getProgress());
    try {
      nowPlayingMedia.viewOffset = Integer.toString(seekBar.getProgress() * 1000);
      castPlayerManager.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    isSeeking = false;
  }
}
