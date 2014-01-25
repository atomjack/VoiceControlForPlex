package us.nineworlds.serenity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.atomjack.vcfpht.GDMService;
import com.atomjack.vcfpht.VoiceControlForPlexApplication;
import com.atomjack.vcfpht.MainActivity;
import com.atomjack.vcfpht.PlayMediaActivity;
import com.atomjack.vcfpht.model.PlexServer;

public class GDMReceiver extends BroadcastReceiver {
	
	@Override
	public void onReceive(Context context, Intent intent) {

		if (intent.getAction().equals(GDMService.MSG_RECEIVED)) {
			String message = intent.getStringExtra("data").trim();
			String ipAddress = intent.getStringExtra("ipaddress").substring(1);
			Log.v(MainActivity.TAG, "message: " + message);
			
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
			matcher.find();
//      if(matcher.matches()) {
        String machineIdentifier = matcher.group(1);

        server.setPort(serverPort);
        server.setName(serverName);
        server.setAddress(ipAddress);
        server.setMachineIdentifier(machineIdentifier);

        VoiceControlForPlexApplication.addPlexServer(server);
//      }
		} else if (intent.getAction().equals(GDMService.SOCKET_CLOSED)) {
			Log.i("GDMService", "Finished Searching");
			Intent i;
			Log.v(MainActivity.TAG, "ORIGIN: " + intent.getStringExtra("ORIGIN"));
			if(intent.getStringExtra("ORIGIN").equals("PlayMediaActivity")) {
				i = new Intent(context, PlayMediaActivity.class);
			} else {
				i = new Intent(context, MainActivity.class);
			}
			i.putExtra("FROM", "GDMReceiver");
			i.putExtra("ORIGIN", intent.getStringExtra("ORIGIN"));
			i.putExtra("queryText", intent.getStringExtra("queryText"));
			i.addFlags(Intent.FLAG_FROM_BACKGROUND);
			i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(i);
		}
	}
	
}
