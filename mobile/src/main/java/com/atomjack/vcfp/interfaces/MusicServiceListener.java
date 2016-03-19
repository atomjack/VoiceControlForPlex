package com.atomjack.vcfp.interfaces;

import com.atomjack.shared.PlayerState;
import com.atomjack.vcfp.model.PlexTrack;

public interface MusicServiceListener {
  void onTimeUpdate(PlayerState state, int time);
  void onTrackChange(PlexTrack track);
  void onFinished();
}
