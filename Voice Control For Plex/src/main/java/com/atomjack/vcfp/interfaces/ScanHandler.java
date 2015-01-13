package com.atomjack.vcfp.interfaces;

import com.atomjack.vcfp.model.PlexDevice;

public interface ScanHandler {
	void onDeviceSelected(PlexDevice device, boolean resume);
}
