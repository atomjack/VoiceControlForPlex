package us.nineworlds.serenity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atomjack.vcfp.GDMService;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexServer;

public class GDMReceiver extends BroadcastReceiver {
	
	@Override
	public void onReceive(Context context, Intent intent) {
		Logger.d("GDMReceiver onReceive: %s", intent.getAction());
		if (intent.getAction().equals(GDMService.MSG_RECEIVED)) {
			String message = intent.getStringExtra("data").trim();
			String ipAddress = intent.getStringExtra("ipaddress").substring(1);

			Logger.d("message: %s", message);
			
			PlexServer server = new PlexServer();
			
			Pattern p = Pattern.compile( "Name: ([^\r]+)", Pattern.DOTALL);
			Matcher matcher = p.matcher(message);
			matcher.find();
			String serverName = matcher.group(1);

			p = Pattern.compile( "Port: ([^\r]+)", Pattern.DOTALL);
			matcher = p.matcher(message);
			matcher.find();
			String serverPort = matcher.group(1);
			
			p = Pattern.compile( "Resource-Identifier: ([0-9a-f-]+)", Pattern.DOTALL);
			matcher = p.matcher(message);
      if(matcher.find()) {
        String machineIdentifier = matcher.group(1);

        server.port = serverPort;
        server.name = serverName;
        server.address = ipAddress;
        server.machineIdentifier = machineIdentifier;

        VoiceControlForPlexApplication.addPlexServer(server);
      }
		} else if (intent.getAction().equals(GDMService.SOCKET_CLOSED)) {
			Logger.i("Finished Searching");
			// Send the reply back to whichever class called for it.
			Class theClass = (Class)intent.getSerializableExtra("class");
			Intent i = new Intent(context, theClass);
			Logger.d("ORIGIN: %s", intent.getStringExtra("ORIGIN"));
			i.setAction(VoiceControlForPlexApplication.Intent.GDMRECEIVE);
			i.putExtra("FROM", "GDMReceiver");
			i.putExtra("ORIGIN", intent.getStringExtra("ORIGIN"));
			i.putExtra("queryText", intent.getStringExtra("queryText"));
			i.addFlags(Intent.FLAG_FROM_BACKGROUND);
			i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			if(theClass.getSuperclass() == Service.class) {
				context.startService(i);
			} else if(theClass.getSuperclass() == Activity.class) {
				context.startActivity(i);
			}
		}
	}
}
