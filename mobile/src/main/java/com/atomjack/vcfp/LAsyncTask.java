package com.atomjack.vcfp;

import android.os.AsyncTask;

import com.atomjack.shared.Logger;

public abstract class LAsyncTask extends AsyncTask<Void, Void, Void> {
  public String label;
  public LimitedAsyncTask.TaskHandler handler;

  @Override
  protected void onPostExecute(Void aVoid) {
    super.onPostExecute(aVoid);
    Logger.d("Done running task %s", label);
    if(handler != null)
      handler.onFinished();
  }
}