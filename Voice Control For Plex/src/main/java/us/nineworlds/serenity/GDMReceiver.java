package us.nineworlds.serenity;

import java.util.ArrayList;
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
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexServer;

public class GDMReceiver extends BroadcastReceiver {

	private ArrayList<PlexClient> clients = new ArrayList<PlexClient>();
	@Override
	public void onReceive(Context context, Intent intent) {
		Logger.d("GDMReceiver onReceive: %s", intent.getAction());
		if (intent.getAction().equals(GDMService.MSG_RECEIVED)) {
			String message = intent.getStringExtra("data").trim();
			String ipAddress = intent.getStringExtra("ipaddress").substring(1);

			Logger.d("message: %s", message);
			


			Pattern p;
			Matcher matcher;

			p = Pattern.compile( "Content-Type: ([^\r]+)", Pattern.DOTALL);
			matcher = p.matcher(message);
			matcher.find();
			String contentType = matcher.group(1);

			p = Pattern.compile( "Name: ([^\r]+)", Pattern.DOTALL);
			matcher = p.matcher(message);
			matcher.find();
			String name = matcher.group(1);

			p = Pattern.compile( "Port: ([^\r]+)", Pattern.DOTALL);
			matcher = p.matcher(message);
			matcher.find();
			String port = matcher.group(1);

			p = Pattern.compile( "Protocol: ([^\r]+)", Pattern.DOTALL);
			matcher = p.matcher(message);
			String protocol = "";
			if(matcher.find())
				protocol = matcher.group(1);

			p = Pattern.compile( "Version: ([0-9a-f-]+)", Pattern.DOTALL);
			matcher = p.matcher(message);
			String version = "";
			if(matcher.find())
				version = matcher.group(1);


			p = Pattern.compile( "Resource-Identifier: ([0-9a-f-]+)", Pattern.DOTALL);
			matcher = p.matcher(message);


			if(matcher.find()) {
        String machineIdentifier = matcher.group(1);

				if(contentType.equals("plex/media-server")) {

					PlexServer server = new PlexServer();
					server.port = port;
					server.name = name;
					server.address = ipAddress;
					server.machineIdentifier = machineIdentifier;
					server.version = version;
					server.local = true;
					Connection connection = new Connection("http", server.address, server.port);
					server.connections = new ArrayList<Connection>();
					server.connections.add(connection);
					VoiceControlForPlexApplication.addPlexServer(server);
				} else if(contentType.equals("plex/media-player") && protocol.equals("plex")) {
					PlexClient client = new PlexClient();
					client.port = port;
					client.name = name;
					client.address = ipAddress;
					client.machineIdentifier = machineIdentifier;
					client.version = version;

					p = Pattern.compile( "Product: ([^\r]+)", Pattern.DOTALL);
					matcher = p.matcher(message);
					if(matcher.find())
						client.product = matcher.group(1);
					clients.add(client);
				}
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
			i.putExtra(VoiceControlForPlexApplication.Intent.SHOWRESOURCE, intent.getBooleanExtra(VoiceControlForPlexApplication.Intent.SHOWRESOURCE, false));

			String scanType = intent.getStringExtra(VoiceControlForPlexApplication.Intent.SCAN_TYPE);
			Logger.d("SCAN TYPE: %s", scanType);
			if(clients.size() > 0 && scanType.equals("client"))
				i.putParcelableArrayListExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENTS, clients);

			i.putExtra(VoiceControlForPlexApplication.Intent.SCAN_TYPE, intent.getStringExtra(VoiceControlForPlexApplication.Intent.SCAN_TYPE));

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
