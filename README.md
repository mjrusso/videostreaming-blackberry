# BlackBerry Video Streaming

This project demonstrates the available mechanisms for streaming video content to BlackBerry smartphones.

## Streaming Options

The following streaming options are enabled by this software:

1. _[RTSP]_ BlackBerry Media Player via Browser
2. _[RTSP]_ Mobile Media API/ JSR-135 (MMAPI)
3. _[HTTP]_ BlackBerry Media Player via Browser
4. _[HTTP]_ Mobile Media API/ JSR-135 (MMAPI)
5. _[HTTP Progressive Download]_ StreamingPlayer API

Options 4) and 5) use HTTP Progressive Download on BlackBerry OS v5.0+.  For older OS versions, the entire file must be downloaded before playback.  If the entire file must be downloaded before playback, and in the event that the file is too large, the device will receive the _HTTP 413 – Request Entity Too Large_ error code and will be unable to play back the media.

Option 5) provides a custom implementation on top of MMAPI (details [here](http://supportforums.blackberry.com/t5/Java-Development/Streaming-media-Start-to-finish/ta-p/488255)), enabling progressive download on devices running pre-5.0 versions of the BlackBerry OS.

## Device Compatibility

Supported media containers, audio and video formats, and streaming options vary based on device type and platform version.  See [Supported Media Types on BlackBerry Smartphones](http://docs.blackberry.com/en/smartphone_users/subcategories/?userType=1&category=Media%20Types%20Supported%20on%20BlackBerry%20Smartphones) for full details.
