package com.mjrusso.blackberry.videostreaming;

import java.io.*;
import javax.microedition.media.*;
import javax.microedition.media.control.*;
import net.rim.device.api.system.*;
import net.rim.device.api.ui.*;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.ui.container.*;

public class MMAPIVideoPlaybackScreen extends BaseVideoPlaybackScreen implements PlayerListener
{

    private Player _player;

    public MMAPIVideoPlaybackScreen(String url)
    {
        super();

        final String urlWithConnectionString = appendConnectionString(url);
        System.out.println("initializing player with URL " + urlWithConnectionString);

        new Thread()
        {
            public void run()
            {
                try
                {
                    _player = javax.microedition.media.Manager.createPlayer(urlWithConnectionString);
                    _player.addPlayerListener((MMAPIVideoPlaybackScreen)_screen);
                    _player.realize();

                    VideoControl control = (VideoControl) _player.getControl("VideoControl");
                    _videoField = (Field) control.initDisplayMode(
                        VideoControl.USE_GUI_PRIMITIVE, "net.rim.device.api.ui.Field"
                    );
                    control.setDisplaySize(Display.getWidth(), Display.getHeight());

                    _player.prefetch();
                    UiApplication.getUiApplication().invokeLater(new Runnable()
                    {
                        public void run()
                        {
                            add(_videoField);
                        }
                    });
                    control.setVisible(true);
                    _player.start();
                }
                catch (IllegalStateException ex)
                {
                    handleException(ex, true);
                }
                catch (IllegalArgumentException ex)
                {
                    handleException(ex, true);
                }
                catch (IOException ex)
                {
                    handleException(ex, true);
                }
                catch(MediaException ex)
                {
                    handleException(ex, true);
                }
            }
        }.start();
    }

    protected void stopPlayback()
    {
        try {
            if (_player != null) _player.stop();
        }
        catch (Exception ex)
        {
            System.out.println(ex.toString());
        }
    }

    /** PlayerListener Implementation */
    public void playerUpdate(Player player, final String event, Object eventData)
    {
        if (event.equals(PlayerListener.ERROR))
        {
            System.out.println("Player Error: " + eventData);
            handleException(new Exception("Player Error: " + eventData), false);
        }
        else if (event.equals(STARTED))
        {
            System.out.println("Player Started");
        }    
        else if (event.equals(STOPPED))
        {
            System.out.println("Player Stopped");
            popScreen();
        }
    
        else if (event.equals(END_OF_MEDIA))
        {
            System.out.println("Player End of Media");
            popScreen();
        }
    }
}
