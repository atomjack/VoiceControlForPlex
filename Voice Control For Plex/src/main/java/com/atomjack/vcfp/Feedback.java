package com.atomjack.vcfp;

import android.content.Context;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

public class Feedback implements TextToSpeech.OnInitListener {
	private SharedPreferences mPrefs;
	private TextToSpeech feedbackTts = null;
	private TextToSpeech errorsTts = null;
	private Context context;
	private String feedbackQueue = null;
	private String errorsQueue = null;

	public Feedback(SharedPreferences prefs, Context ctx) {
		mPrefs = prefs;
		context = ctx;
	}

	@Override
	public void onInit(int i) {
		Logger.d("Feedback onInit");
		if(errorsTts != null)
			errorsTts.setLanguage(VoiceControlForPlexApplication.getVoiceLocale(mPrefs.getString(VoiceControlForPlexApplication.PREF_ERRORS_VOICE, "Locale.US")));
		if(feedbackTts != null)
			feedbackTts.setLanguage(VoiceControlForPlexApplication.getVoiceLocale(mPrefs.getString(VoiceControlForPlexApplication.PREF_FEEDBACK_VOICE, "Locale.US")));

		if(errorsQueue != null) {
			feedback(errorsQueue, true);
			errorsQueue = null;
		}
		if(feedbackQueue != null) {
			feedback(feedbackQueue, true);
			feedbackQueue = null;
		}
	}

	public void m(String text, Object... arguments) {
		text = String.format(text, arguments);
		m(text);
	}

	public void e(String text, Object... arguments) {
		text = String.format(text, arguments);
		e(text);
	}

	public void m(String text) {
		feedback(text, false);
	}

	public void e(String text) {
		feedback(text, true);
	}

	private void feedback(String text, boolean errors) {
		if(mPrefs.getInt(errors ? "errors" : "feedback", 0) == MainActivity.FEEDBACK_VOICE) {
			TextToSpeech tts = errors ? errorsTts : feedbackTts;
			if (tts == null) {
				// This tts not set up yet, so initiate it and add the text to be spoken to the appropriate queue.
				// The text will be spoken when the tts is finished setting up (in onInit)
				if (errors) {
					errorsTts = new TextToSpeech(context, this);
					errorsQueue = text;
				} else {
					feedbackTts = new TextToSpeech(context, this);
					feedbackQueue = text;
				}
			} else {
				tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
				if (errors)
					errorsQueue = null;
				else
					feedbackQueue = null;
			}
		} else {
			Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
		}
	}
}
