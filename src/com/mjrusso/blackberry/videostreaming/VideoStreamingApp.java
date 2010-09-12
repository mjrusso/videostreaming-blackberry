package com.mjrusso.blackberry.videostreaming;

import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.component.Dialog;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.RichTextField;

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


