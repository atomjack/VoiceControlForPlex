package com.atomjack.vcfp.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import com.atomjack.vcfp.R;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;

import butterknife.ButterKnife;

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
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    ButterKnife.bind(this, view);
  }

  @Override
  protected void doRewind() {
    if(position > -1) {
      client.seekTo((position * 1000) - 15000, nowPlayingMedia.isMusic() ? "music" : "video", new PlexHttpResponseHandler() {
        @Override
        public void onSuccess(PlexResponse response) {
          position -= 15;
        }

        @Override
        public void onFailure(Throwable error) {

        }
      });
    }
  }

  @Override
  protected void doForward() {
    if(position > -1) {
      client.seekTo((position * 1000) + 30000, nowPlayingMedia.isMusic() ? "music" : "video", new PlexHttpResponseHandler() {
        @Override
        public void onSuccess(PlexResponse response) {
          position += 30;
        }

        @Override
        public void onFailure(Throwable error) {

        }
      });
    }
  }

  @Override
  protected void doPlay() {
    client.play(nowPlayingMedia.isMusic() ? "music" : "video", null);
  }

  @Override
  protected void doPause() {
    client.pause(nowPlayingMedia.isMusic() ? "music" : "video", null);
  }

  @Override
  protected void doStop() {
    client.stop(nowPlayingMedia.isMusic() ? "music" : "video", null);
  }

  @Override
  protected void doNext() {
    client.next(nowPlayingMedia.isMusic() ? "music" : "video", null);
  }

  @Override
  protected void doPrevious() { client.previous(nowPlayingMedia.isMusic() ? "music" : "video", null); }

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
        if(!error.getMessage().matches(".*Failure: 200 OK\n.*")) // Some plex clients don't return valid xml, instead returning "Failure: 200 OK" even though the request succeeded
          feedback.e(String.format(getString(R.string.error_seeking), error.getMessage()));
      }
    });

  }
}
