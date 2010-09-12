package com.mjrusso.blackberry.videostreaming;

import java.io.*;
import javax.microedition.media.*; 
import javax.microedition.media.control.*;
import net.rim.blackberry.api.browser.Browser;
import net.rim.device.api.system.*;
import net.rim.device.api.ui.*;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.ui.container.*;

public class MMAPIPlaybackChoice extends PlaybackChoice
{

    public MMAPIPlaybackChoice(String label)
    {
        super(label);
    }
    
    public void play(final String url)
    {
        final UiApplication app = UiApplication.getUiApplication();
        app.invokeLater(new Runnable() {
            public void run()
            {
                app.pushScreen(new VideoPlaybackScreen(url, true));
            }
        });
    }
   
}
