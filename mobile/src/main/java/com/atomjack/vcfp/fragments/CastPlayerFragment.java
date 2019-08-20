package com.atomjack.vcfp.fragments;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.view.View;
import android.widget.SeekBar;

import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.services.SubscriptionService;

import butterknife.ButterKnife;

public class CastPlayerFragment extends PlayerFragment {
  private SubscriptionService subscriptionService;

  public CastPlayerFragment() {
    super();
  }

  public interface CastPlayerFragmentListener {
    SubscriptionService getSubscriptionService();
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    ButterKnife.bind(this, view);
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    try {
      CastPlayerFragmentListener listener = (CastPlayerFragmentListener) getActivity();
      subscriptionService = listener.getSubscriptionService();
    } catch (ClassCastException e) {
      throw new ClassCastException(getActivity().toString()
              + " must implement OnHeadlineSelectedListener");
    }
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
      subscriptionService.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
    }
  }

  @Override
  protected void doForward() {
    logger.d("Doing forward, position: %d", position);
    if(position > -1) {
      nowPlayingMedia.viewOffset = Integer.toString((position * 1000) + 30000);
      subscriptionService.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
    }
  }

  @Override
  protected void doPlay() {
    subscriptionService.play();
  }

  @Override
  protected void doPause() {
    subscriptionService.pause();
  }

  @Override
  protected void doStop() {
    try {
      subscriptionService.stop();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  @Override
  protected void doNext() {
    subscriptionService.next();
  }

  @Override
  protected void doPrevious() {
    subscriptionService.previous();
  }

  @Override
  public void onStopTrackingTouch(SeekBar seekBar) {
    logger.d("stopped changing progress: %d", seekBar.getProgress());
    try {
      nowPlayingMedia.viewOffset = Integer.toString(seekBar.getProgress() * 1000);
      subscriptionService.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    isSeeking = false;
  }
}
