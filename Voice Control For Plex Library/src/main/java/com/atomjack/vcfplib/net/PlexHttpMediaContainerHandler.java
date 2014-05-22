package com.atomjack.vcfplib.net;

import com.atomjack.vcfplib.model.MediaContainer;

public interface PlexHttpMediaContainerHandler
{
  void onSuccess(MediaContainer mediaContainer);
	void onFailure(Throwable error);
}
