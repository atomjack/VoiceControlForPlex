package com.atomjack.vcfp.interfaces;

import com.atomjack.shared.PlayerState;
import com.atomjack.shared.model.Timeline;

public interface PlayerStateHandler {
  public void onSuccess(PlayerState playerState, Timeline timeline);
}
