package com.atomjack.vcfp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;

import com.atomjack.shared.Logger;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.WearConstants;
import com.google.android.gms.wearable.DataMap;

public class VoiceInputActivity extends Activity {
  public static final String START_SPEECH_RECOGNITION = "com.atomjack.vcfp.intent.start_speech_recognition";
  private static final int SPEECH_RECOGNIZER_REQUEST_CODE = 0;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Intent intent = getIntent();
    if(intent != null) {
      if(intent.getAction() != null) {
        String action = intent.getAction();
        Logger.d("[VoiceInputActivity] onNewIntent: %s", action);
        if (action.equals(START_SPEECH_RECOGNITION)) {
          Logger.d("[VoiceInputActivity] starting speech recognition.");
          Logger.d("client: %s", intent.getStringExtra(WearConstants.CLIENT_NAME));
          Intent recIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
          recIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
          startActivityForResult(recIntent, SPEECH_RECOGNIZER_REQUEST_CODE);

        }
      }
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);

  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Logger.d("[VoiceInputActivity] onActivityResult: %s", requestCode);
    if (requestCode == SPEECH_RECOGNIZER_REQUEST_CODE) {
      // When the speech recognizer finishes its work, Android invokes this callback with requestCode equal to SPEECH_RECOGNIZER_REQUEST_CODE
      if (resultCode == RESULT_OK) {
        DataMap dataMap = new DataMap();
        dataMap.putStringArrayList(WearConstants.SPEECH_QUERY, data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));
        new SendToDataLayerThread(WearConstants.SPEECH_QUERY, dataMap, VoiceInputActivity.this).start();
        finish();
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

}
