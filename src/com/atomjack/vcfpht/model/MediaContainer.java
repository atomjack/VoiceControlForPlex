package com.atomjack.vcfpht.model;

import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class MediaContainer {
	@Attribute(required=false)
	private String machineIdentifier;
	
	@Attribute(required=false)
	private String friendlyName;
	
	@Attribute(required=false)
	public String title1;
	
	@Attribute(required=false)
	public String grandparentTitle;
	
	@ElementList(required=false, inline=true, name="Server")
	public List<PlexClient> clients = new ArrayList<PlexClient>();
	
	public List<Server> servers = new ArrayList<Server>();
	
	@ElementList(required=false, inline=true, entry="Directory")
	public List<PlexDirectory> directories = new ArrayList<PlexDirectory>();
	
	@ElementList(required=false, inline=true, entry="Video")
	public List<PlexVideo> videos = new ArrayList<PlexVideo>();
	
	public String getFriendlyName() {
		return friendlyName;
	}
	public void setFriendlyName(String name) {
		this.friendlyName = name;
	}
	public String getMachineIdentifier() {
		return machineIdentifier;
	}
	public void setMachineIdentifier(String machineIdentifier) {
		this.machineIdentifier = machineIdentifier;
	}
}
