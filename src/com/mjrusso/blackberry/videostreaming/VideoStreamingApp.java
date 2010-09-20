package com.mjrusso.blackberry.videostreaming;

import net.rim.device.api.ui.*;

public class VideoStreamingApp extends UiApplication
{
    public static void main(String[] args)
    {
        VideoStreamingApp theApp = new VideoStreamingApp();
        theApp.enterEventDispatcher();
    }

    public VideoStreamingApp()
    {
        pushScreen(new SelectionScreen());
    }
}
