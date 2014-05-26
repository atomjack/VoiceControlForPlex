package com.atomjack.vcfp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class PlayReceiver extends BroadcastReceiver
{
	@Override
	public void onReceive(final Context context, final Intent intent)
	{
		Logger.d("PLAYRECEIVER");
		Bundle bundle = intent.getExtras();
		String arg = bundle.getString("com.atomjack.vcfp.intent.ARGUMENTS");
		Logger.d("arg: %s", arg);

		Intent sendIntent = new Intent(context, PlayMediaActivity.class);
		sendIntent.putExtra("queryText", arg);
		sendIntent.putExtra("ORIGIN", "Tasker");
		sendIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
		sendIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(sendIntent);
	}
}
