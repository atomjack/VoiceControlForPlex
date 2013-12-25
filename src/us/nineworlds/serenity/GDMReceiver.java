package us.nineworlds.serenity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.atomjack.vcfpht.GDMService;
import com.atomjack.vcfpht.GoogleSearchPlexControlApplication;
import com.atomjack.vcfpht.MainActivity;
import com.atomjack.vcfpht.model.PlexServer;

public class GDMReceiver extends BroadcastReceiver {
	
	@Override
	public void onReceive(Context context, Intent intent) {

		if (intent.getAction().equals(GDMService.MSG_RECEIVED)) {
			String message = intent.getStringExtra("data").trim();
			String ipAddress = intent.getStringExtra("ipaddress").substring(1);
			Log.v(MainActivity.TAG, "message: " + message);
			
			PlexServer server = new PlexServer();
			
			int namePos = message.indexOf("Name: ");
			namePos += 6;
			int crPos = message.indexOf("\r", namePos);
			String serverName = message.substring(namePos, crPos);
			int portPos = message.indexOf("Port: ");
			portPos += 6;
			String serverPort = message.substring(portPos, message.indexOf("\r", portPos));
			
			server.setPort(serverPort);
			server.setName(serverName);
			server.setIPAddress(ipAddress);
			GoogleSearchPlexControlApplication.addPlexServer(server);
		} else if (intent.getAction().equals(GDMService.SOCKET_CLOSED)) {
			Log.i("GDMService", "Finished Searching");
			Intent i = new Intent(context, MainActivity.class);
			i.putExtra("FROM", "GDMReceiver");
			i.addFlags(Intent.FLAG_FROM_BACKGROUND);
			i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(i);
		}
	}
	
}
