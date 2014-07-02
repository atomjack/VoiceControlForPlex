package com.atomjack.vcfp;

import android.content.Context;

import com.atomjack.vcfp.activities.VCFPActivity;

public class VCFPSingleton {
  private static VCFPSingleton instance;
  private PlexSubscription plexSubscription;
//  private Context mContext;

  public static synchronized VCFPSingleton getInstance()
  {
    // Return the instance
    if(instance == null)
      instance = new VCFPSingleton();
    return instance;
  }

  public PlexSubscription getPlexSubscription() {
    if(plexSubscription == null)
      plexSubscription = new PlexSubscription();
    return plexSubscription;
  }

  private VCFPSingleton()
  {
    // Constructor hidden because this is a singleton
  }

  public void customSingletonMethod()
  {
    // Custom method
  }

  public Object clone() throws CloneNotSupportedException
  {
    throw new CloneNotSupportedException();
  }
}
