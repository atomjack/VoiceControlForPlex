package com.atomjack.vcfp.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import com.atomjack.shared.PlayerState;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;

public class PlexPlayerFragment extends PlayerFragment {
  public PlexPlayerFragment() {
    super();
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return super.onCreateView(inflater, container, savedInstanceState);
  }

  @Override
  protected void doRewind() {
    if(position > -1) {
      client.seekTo((position * 1000) - 15000, nowPlayingMedia.isMusic() ? "music" : "video", null);
    }
  }

  @Override
  protected void doForward() {
    if(position > -1) {
      client.seekTo((position * 1000) + 30000, nowPlayingMedia.isMusic() ? "music" : "video", null);
    }
  }

  @Override
  protected void doPlayPause() {
    if(currentState == PlayerState.PLAYING) {
      client.pause(null);
    } else if(currentState == PlayerState.PAUSED) {
      client.play(null);
    }
  }

  @Override
  protected void doStop() {
    client.stop(null);
  }

  @Override
  protected void doNext() {
    client.next(null);
  }

  @Override
  protected void doPrevious() {
    client.previous(null);
  }

  @Override
  public void onStopTrackingTouch(SeekBar _seekBar) {
    client.seekTo(_seekBar.getProgress()*1000, nowPlayingMedia.isMusic() ? "music" : "video", new PlexHttpResponseHandler() {
      @Override
      public void onSuccess(PlexResponse response) {
        isSeeking = false;
      }

      @Override
      public void onFailure(Throwable error) {
        isSeeking = false;
        feedback.e(String.format(getString(R.string.error_seeking), error.getMessage()));
      }
    });

  }
}
