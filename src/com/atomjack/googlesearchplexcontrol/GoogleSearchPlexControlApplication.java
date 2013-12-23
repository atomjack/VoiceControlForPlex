package com.atomjack.googlesearchplexcontrol;

import java.util.concurrent.ConcurrentHashMap;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.content.res.Resources.NotFoundException;
import android.util.Log;

import com.atomjack.googlesearchplexcontrol.model.MediaContainer;
import com.atomjack.googlesearchplexcontrol.model.PlexServer;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

public class GoogleSearchPlexControlApplication {
    private static ConcurrentHashMap<String, PlexServer> plexmediaServers = new ConcurrentHashMap<String, PlexServer>();
    
    public static ConcurrentHashMap<String, PlexServer> getPlexMediaServers() {
		return plexmediaServers;
	}
    
    private static Serializer serial = new Persister();
    
    public static void addPlexServer(final PlexServer server) {
    	if (!plexmediaServers.containsKey(server.getName())) {
    		try {
    		    String url = "http://" + server.getIPAddress() + ":32400/library/sections/";
    		    AsyncHttpClient client = new AsyncHttpClient();
    		    client.get(url, new AsyncHttpResponseHandler() {
    		        @Override
    		        public void onSuccess(String response) {
    		            Log.v(MainActivity.TAG, "HTTP REQUEST: " + response);
    		            MediaContainer mc = new MediaContainer();
    		            try {
    		            	mc = serial.read(MediaContainer.class, response);
    		            } catch (NotFoundException e) {
    		                e.printStackTrace();
    		            } catch (Exception e) {
    		                e.printStackTrace();
    		            }
    		            for(int i=0;i<mc.directories.size();i++) {
    		            	Log.v(MainActivity.TAG, "Directory type: " + mc.directories.get(i).getType());
    		            	if(mc.directories.get(i).getType().equals("movie")) {
    		            		server.addMovieSection(mc.directories.get(i).getKey());
    		            	}
    		            	if(mc.directories.get(i).getType().equals("show")) {
    		            		server.addTvSection(mc.directories.get(i).getKey());
    		            	}
    		            }
    		            Log.v(MainActivity.TAG, "title1: " + mc.title1);
    		            if(mc.directories != null)
    		            	Log.v(MainActivity.TAG, "Directories: " + mc.directories.size());
    		            else
    		            	Log.v(MainActivity.TAG, "No directories found!");
    		            plexmediaServers.putIfAbsent(server.getName(), server);
//    		            getClients(mc);
    		        }
    		    });

    		} catch (Exception e) {
    			Log.e(MainActivity.TAG, "Exception getting clients: " + e.toString());
    		}
    		
			Log.d(MainActivity.TAG, "Adding " + server.getName());
		} else {
			Log.d(MainActivity.TAG, server.getName() + " already added.");
		}
    }
    
}
