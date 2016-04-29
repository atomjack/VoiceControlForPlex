package com.atomjack.vcfp.interfaces;

import com.atomjack.vcfp.model.Stream;

// This interface is used for the PlayerFragment to pass messages on to the activity
public interface ActivityListener {
  void onLayoutNotFound();
  void setStream(Stream stream);
}