#Voice Control for Plex

A Plug-in for Google Search API for Plex

## Requirements
You must have a rooted phone, the Xposed Framework (http://forum.xda-developers.com/showthread.php?t=1574401), and the Google Search API (http://forum.xda-developers.com/showthread.php?t=2554173)

## Setup

Running the application will allow you to set a default Plex Media Server to play media from. If you do not set this, it will scan all available servers. If you only have one server, it is recommended to set it here as scanning for servers each time playback is triggered will cause a slight delay. You can also choose which client to stream to. However, you can always specify the client to stream to by appending "on &lt;client name&gt;" to the phrase you speak into Google Now (for example, if you have two clients, named "plex" and "laptop", you could say "Watch Aliens on plex", or "Watch The Dark Knight on laptop").

## Usage

Below are examples of what to say to Google Now to trigger playback. Sentence fragments in bold are required **AS IS**, while fragments in italics depend on the show, season and episode you wish to watch. If "Resume if in progress" is not checked, you can resume playback by speaking "Resume watching" instead of "Watch" (e.g. "Resume watching Inception")

### For movies:
"**Watch** *Aliens*"

"**Watch movie** *V For Vendetta*"


### For TV Shows:
"**Watch Season** *1* **Episode** *1* **of** *Homeland*"

"**Watch** *The Newsroom* **Season** *1* **Episode** *2*"

"**Watch** *Breaking Bad* **Season** *5* **Episode** *8*"

"**Watch episode** *Once More With Feeling* **of** *Buffy The Vampire Slayer*" (If the name of the show contains the word "of", you should use the next example instead of this one, as this one won't work)

"**Watch** *Game of Thrones* **episode** *The Rains of Castamere*"

"**Watch the next episode of** *The Walking Dead*" - uses Plex's "on deck"

"**Watch the latest episode of** *The Daily Show with Jon Stewart*" - to play the most recent episode by air date.


### For Music:
"**Listen to** *Black Sands* **by** *Bonobo*" - Listen to &lt;song&gt; by &lt;artist&gt;

"**Listen to the album** *Drink The Sea* **by** *The Glitch Mob*" - Listen to &lt;album&gt; by &lt;artist&gt;

"**Listen to the album** *Music Has The Right To Children*" - Listen to &lt;album&gt; (artist is optional, specify if more than one match found)
