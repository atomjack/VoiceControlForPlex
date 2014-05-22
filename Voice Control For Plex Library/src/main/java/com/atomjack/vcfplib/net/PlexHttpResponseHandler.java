package com.atomjack.vcfplib.net;

import com.atomjack.vcfplib.model.PlexResponse;

public interface PlexHttpResponseHandler {
  void onSuccess(PlexResponse response);
	void onFailure(Throwable error);
}
