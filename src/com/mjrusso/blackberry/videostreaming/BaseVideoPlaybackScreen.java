package com.mjrusso.blackberry.videostreaming;

import java.io.*;
import javax.microedition.media.*;
import javax.microedition.media.control.*;
import net.rim.device.api.system.*;
import net.rim.device.api.ui.*;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.ui.container.*;
import rimx.media.streaming.StreamingPlayer;
import rimx.media.streaming.StreamingPlayerListener;

public abstract class BaseVideoPlaybackScreen extends FullScreen
{
    protected BaseVideoPlaybackScreen _screen;
    protected Field _videoField;

    public BaseVideoPlaybackScreen()
    {
        _screen = this;
    }

    protected abstract void stopPlayback();

    protected String appendConnectionString(String url)
    {
        if (DeviceInfo.isSimulator()) { }
        else
        {
            // currently only support WiFi connections for on-device streaming
            url = url.concat(";interface=wifi");
        }
        return url;
    }

    protected void popScreen()
    {
        synchronized(UiApplication.getEventLock()) {
            UiApplication.getUiApplication().popScreen(_screen);
        }
        stopPlayback();
    }

    protected void handleException(final Exception ex)
    {
        System.out.println(ex.toString());
        final UiApplication app = UiApplication.getUiApplication();
        app.invokeLater(new Runnable() {
            public void run()
            {
                app.popScreen(_screen);
                Dialog.alert(ex.toString());
            }
        });
        stopPlayback();
    }

    protected boolean keyChar(char key, int status, int time) {
        switch (key) {
            case Characters.ESCAPE:
                popScreen();
                return true;
        }
        return false;
    }

}
