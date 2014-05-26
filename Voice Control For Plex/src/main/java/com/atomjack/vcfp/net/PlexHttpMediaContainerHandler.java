package com.atomjack.vcfp.net;

import com.atomjack.vcfp.model.MediaContainer;

public interface PlexHttpMediaContainerHandler
{
  void onSuccess(MediaContainer mediaContainer);
	void onFailure(Throwable error);
}
