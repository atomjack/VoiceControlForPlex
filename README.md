#Voice Control for Plex Home Theater

A Plug-in for Google Search API for Plex Home Theater

## Requirements
You must have a rooted phone, the Xposed Framework (http://forum.xda-developers.com/showthread.php?t=1574401), and the Google Search API (http://forum.xda-developers.com/showthread.php?t=2554173)

## Setup

Running the application for the first time will immediately start scanning for Plex Media Servers running on your local WiFi network. Once you choose one, it will show the available Plex clients that media will play on. Then simply open Google Now and tell Plex to start playing some media. See the section below on Usage for examples. After selection of server and client, you can simply tap the line for the server or client to change it. 

## Usage

Below are examples of what to say to Google Now to trigger playback. Sentence fragments in bold are required **AS IS**, while fragments in italics depend on the show, season and episode you wish to watch. If "Resume if in progress" is not checked, you can resume playback by speaking "Resume watching" instead of "Watch" (e.g. "Resume watching Inception")

### For movies:
"**Watch** *Aliens*"

"**Watch movie** *V For Vendetta*"


### For TV Shows:
"**Watch Season** *1* **Episode** *1* **of** *Homeland*"

"**Watch** *The Newsroom* **Season** *1* **Episode** *2*"

"**Watch** *Breaking Bad* **Season** *5* **Episode** *8*"

"**Watch episode** *Once More With Feeling* **of** *Buffy The Vampire Slayer*"

"**Watch** *Game of Thrones* **episode** *The Rains of Castamere*"

"**Watch the latest episode of** *The Walking Dead*" - uses Plex's "on deck"

