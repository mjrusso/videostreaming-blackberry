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

public class StreamingPlayerVideoPlaybackScreen extends BaseVideoPlaybackScreen implements StreamingPlayerListener
{

    private StreamingPlayer _player;

    public StreamingPlayerVideoPlaybackScreen(String url)
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
                    _player = new StreamingPlayer(urlWithConnectionString, "video/mp4");
                    _player.addStreamingPlayerListener((StreamingPlayerVideoPlaybackScreen) _screen);
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
                    handleException(ex);
                }
                catch (IllegalArgumentException ex)
                {
                    handleException(ex);
                }
                catch (IOException ex)
                {
                    handleException(ex);
                }
                catch(MediaException ex)
                {
                    handleException(ex);
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

    /** StreamingPlayerListener Implementation */
    public void bufferStatusChanged(long bufferStartsAt, long len) { }

    public void downloadStatusUpdated(final long totalDownloaded) { }

    public void feedPaused(final long available) { }

    public void feedRestarted(final long available) { }

    public void initialBufferCompleted(final long available) { }

    public void playerUpdate(String event, Object eventData)
    {
        if (event.equals(PlayerListener.STARTED))
        {
            System.out.println("Player Started");
        }    
        else if (event.equals(PlayerListener.STOPPED))
        {
            System.out.println("Player Stopped");
            popScreen();
        }
        else if (event.equals(PlayerListener.END_OF_MEDIA))
        {
            System.out.println("Player End of Media");
            popScreen();
        }
    }

    public byte[] preprocessData(byte[] bytes, int off, int len)
    {
        return null;
    }
    
    public void nowReading(long now) { }

    public void nowPlaying(long now) { }

    public void contentLengthUpdated(long contentLength) { }

    public void streamingError(final int code)
    {
        switch(code)
        {
            case StreamingPlayerListener.ERROR_DOWNLOADING:
                System.out.println("Player Error: ERROR_DOWNLOADING");
                handleException(new Exception("Player Error: ERROR_DOWNLOADING"));
                break;
            case StreamingPlayerListener.ERROR_SEEKING:
                System.out.println("Player Error: ERROR_SEEKING");
                handleException(new Exception("Player Error: ERROR_SEEKING"));
                break;
            case StreamingPlayerListener.ERROR_OPENING_CONNECTION:
                System.out.println("Player Error: ERROR_OPENING_CONNECTION");
                handleException(new Exception("Player Error: ERROR_OPENING_CONNECTION"));
                break;
            case StreamingPlayerListener.ERROR_PLAYING_MEDIA:
                System.out.println("Player Error: ERROR_PLAYING_MEDIA");
                handleException(new Exception("Player Error: ERROR_PLAYING_MEDIA"));
                break;
        }

    }
}
