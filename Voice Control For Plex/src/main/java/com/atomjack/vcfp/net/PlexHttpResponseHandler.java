package com.atomjack.vcfp.net;

import com.atomjack.vcfp.model.PlexResponse;

public interface PlexHttpResponseHandler {
  void onSuccess(PlexResponse response);
	void onFailure(Throwable error);
}
