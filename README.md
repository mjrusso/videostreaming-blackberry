# BlackBerry Video Streaming

Demonstrating the available mechanisms for streaming video content to BlackBerry smartphones.

## Streaming Options

This project enables the following streaming options:

1. _[RTSP & HTTP]_ BlackBerry Media Player via Browser
2. _[RTSP & HTTP]_ Mobile Media API/ JSR-135 (MMAPI)
3. _[HTTP]_ StreamingPlayer API

### Device Compatibility

#### Containers and Codecs

Supported media containers and audio/ video formats vary based on device type and platform version.

See [Supported Media Types on BlackBerry Smartphones](http://docs.blackberry.com/en/smartphone_users/subcategories/?userType=1&category=Media%20Types%20Supported%20on%20BlackBerry%20Smartphones) for full details.

#### Streaming Options

##### RTSP

RTSP was first made available on EVDO devices running OS v4.3.0 and is now available on EVDO, EDGE, 3G, and WiFi on devices running OS v4.5.0 and above.

##### HTTP

###### Browser and MMAPI

On devices running OS versions prior to v5.0, multimedia files accessed over HTTP must to be downloaded in their _entirety_ before playback can commence.  In cases where these files have a file size that is larger than the device can support, an _HTTP 413 – Request Entity Too Large_ error code will be returned to the application and playback will be aborted.

On devices running OS versions v5.0 and later, HTTP Progressive Download is supported.  With HTTP Progressive Download, small chunks of the multimedia file can be downloaded and played.  (The entire file does not need to be downloaded in its entirety before playback can commence.)

###### StreamingPlayer API

In contrast to the other HTTP playback mechanisms, the [Streaming Player API](http://supportforums.blackberry.com/t5/Java-Development/Streaming-media-Start-to-finish/ta-p/488255) provides a custom playback mechanism that enables HTTP Progressive Download on devices running BlackBerry OS versions prior to v5.0.

## Instructions

### Build/ Run

Add `VideoStreaming.jdp` to a new or existing workspace in the [BlackBerry JDE](http://na.blackberry.com/eng/developers/javaappdev/javadevenv.jsp).  JDE v5.0 or higher is recommended.

To compile, select _Build All_ from the JDE's _Build_ menu.

To run in the simulator, select _Build All and Run_ from the _Build_ menu.  To install on device, use the `javaloader` tool or serve the generated `.jad` and `.cod` files from a web server for OTA installation.

### Caveats

This project currently only supports WiFi for on-device streaming via MMAPI and StreamingPlayer.  Support for additional transports can be added by modifying the `appendConnectionString` method of `BaseVideoPlaybackScreen`.  The [Network Diagnostic Tool KB Article](http://www.blackberry.com/knowledgecenterpublic/livelink.exe/fetch/2000/348583/800451/800563/What_Is_-_Network_Diagnostic_Tool.html?nodeid=1450596&vernum=0) and [associated sample code](http://www.blackberry.com/knowledgecentersupport/kmsupport/developerknowledgebase/zip/NetworkDiagnosticPublic.zip) provide a good starting point for integrating multiple transport types into your application.  (Regardless, WiFi is **highly** recommended for multimedia streaming.)

## Screenshots

![screenshots](https://github.com/mjrusso/videostreaming-blackberry/raw/master/assets/screenshots.png)
