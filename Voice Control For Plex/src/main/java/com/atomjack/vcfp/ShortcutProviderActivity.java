package com.atomjack.vcfp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class ShortcutProviderActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent.ShortcutIconResource icon = Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher);

		Intent sendIntent = new Intent();

		Intent launchIntent = new Intent(this, ShortcutActivity.class);

		sendIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
		sendIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getResources().getString(R.string.app_name));
		sendIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);

		setResult(RESULT_OK, sendIntent);
		finish();
	}
}
