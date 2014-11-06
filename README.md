#Voice Control for Plex

An Android app to control Plex clients with your voice.

## Requirements
There are four ways to use Voice Control for Plex. One way requires root, the others do not.

1. Voice Control for Plex homescreen Shortcut

    Simply add the included homescreen shortcut via the menu button. You will be given the choice of specifying which server and client the shortcut will use, or for the shortcut to use whichever server and client are set in the Main screen, at the time the shortcut is launched.
1. Tasker + utter!

    Requires Tasker (https://play.google.com/store/apps/details?id=net.dinglisch.android.taskerm) and utter! (https://play.google.com/store/apps/details?id=com.brandall.nutter). You must also go into utter! Settings->Advanced Settings->Try Again->select "Send to Tasker".
1. Tasker + AutoVoice

    Requires Tasker (https://play.google.com/store/apps/details?id=net.dinglisch.android.taskerm) and AutoVoice (https://play.google.com/store/apps/details?id=com.joaomgcd.autovoice).
1. Xposed Framework & Google Search API (requires root)

    To use this method, you must have the Xposed Framework (http://forum.xda-developers.com/showthread.php?t=1574401) and the Google Search API (http://forum.xda-developers.com/showthread.php?t=2554173). Voice input is done through Google Search/Now.

For the utter! and AutoVoice methods, the app includes a Tasker project which you must import in order for Tasker to send your voice input to Voice Control for Plex. The settings screen has an button which will import this project - however you will have to then go into Tasker and finish the import process. Instructions are shown when using the button.

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


### Playback control:
"**Pause Playback**"

"**Resume Playback**"

"**Stop Playback**"


### Seeking:
"**Offset** *1* **hour(s)** *15* **minute(s)** *30* **second(s)**"

"**Offset** *25* **minute(s)** *50* **second(s)**"

"**Offset** *2* **hour(s)** *32* **minutes(s)**"

Any combination of hour(s), minute(s), and second(s) is accepted, as long as they are specified in that order. You may also substitute "Timecode" for "Offset".

### Localization
If you'd like to add translations for your language, please see the wiki entry: https://github.com/atomjack/VoiceControlForPlex/wiki/Localization-Instructions
