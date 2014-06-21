package com.atomjack.vcfp;

public interface AfterTransientTokenRequest {
	void success(String token);
	void failure();
}
