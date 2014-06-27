package com.atomjack.vcfp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PlexSubscriptionReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Class theClass = (Class)intent.getSerializableExtra(PlexSubscriptionService.EXTRA_CLASS);
		Intent i = new Intent(context, theClass);
		i.setAction(intent.getAction());
		i.addFlags(Intent.FLAG_FROM_BACKGROUND);
		i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		if(intent.getParcelableArrayListExtra(PlexSubscriptionService.EXTRA_TIMELINES) != null)
			i.putParcelableArrayListExtra(PlexSubscriptionService.EXTRA_TIMELINES, intent.getParcelableArrayListExtra(PlexSubscriptionService.EXTRA_TIMELINES));
		context.startActivity(i);
	}
}
