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

public class VideoPlaybackScreen extends FullScreen implements PlayerListener, StreamingPlayerListener
{
    private VideoPlaybackScreen _screen;
    private Field _videoField;
    
    public VideoPlaybackScreen(String url, final boolean useDirectMMAPI)
    {
        _screen = this;
        final String urlWithConnectionString = appendConnectionString(url);
        
        System.out.println("initializing player with URL " + urlWithConnectionString);
        
        new Thread()
        {
            public void run()
            {
                try
                {
                    if (useDirectMMAPI)
                    {
                        Player player = javax.microedition.media.Manager.createPlayer(urlWithConnectionString);
                        player.addPlayerListener(_screen); 
                        player.realize();
                        
                        VideoControl control = (VideoControl) player.getControl("VideoControl");
                        _videoField = (Field) control.initDisplayMode(
                            VideoControl.USE_GUI_PRIMITIVE, "net.rim.device.api.ui.Field"
                        );
                        control.setDisplaySize(Display.getWidth(), Display.getHeight());
                        
                        player.prefetch();
                        UiApplication.getUiApplication().invokeLater(new Runnable()
                        {
                            public void run()
                            {
                                add(_videoField);
                            }
                        });
                        control.setVisible(true);
                        player.start();
                    }
                    else
                    {
                        StreamingPlayer player = new StreamingPlayer(urlWithConnectionString, "video/mp4");
                        player.addStreamingPlayerListener(_screen); 
                        player.realize();
                        
                        VideoControl control = (VideoControl) player.getControl("VideoControl");
                        _videoField = (Field) control.initDisplayMode(
                            VideoControl.USE_GUI_PRIMITIVE, "net.rim.device.api.ui.Field"
                        );
                        control.setDisplaySize(Display.getWidth(), Display.getHeight());
                        
                        player.prefetch();
                        UiApplication.getUiApplication().invokeLater(new Runnable()
                        {
                            public void run()
                            {
                                add(_videoField);
                            }
                        });
                        control.setVisible(true);
                        player.start();
                    }
                    
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
    
    private String appendConnectionString(String url)
    {
        if (DeviceInfo.isSimulator())
        {
            //url = url.concat(";deviceside=true");
        }
        else
        {
            // TODO: we currently only support WiFi connections
            url = url.concat(";interface=wifi");
        }
        return url;
    }
    
    private void popScreen()
    {
        synchronized(UiApplication.getEventLock()) {
            UiApplication.getUiApplication().popScreen(_screen); 
        }
    }
    
    private void handleException(final Exception ex)
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
    }
    
    protected boolean keyChar(char key, int status, int time) {
        switch (key) {
            case Characters.ESCAPE:
                popScreen();
                return true;
        }
        return false;
    }
    
    /** PlayerListener Implementation */
    public void playerUpdate(Player player, final String event, Object eventData)
    {
        if (event.equals(PlayerListener.ERROR))
        {
            System.out.println("Player Error: " + eventData);
            handleException(new Exception("Player Error: " + eventData));
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
    
    /** StreamingPlayerListener Implementation */
    public void bufferStatusChanged(long bufferStartsAt, long len)
    {
    }

    public void downloadStatusUpdated(final long totalDownloaded)
    {                                                                   
    }
    
    public void feedPaused(final long available)
    {
    }

    public void feedRestarted(final long available)
    {
    }

    public void initialBufferCompleted(final long available)
    {
    }

    public void playerUpdate(String event, Object eventData)
    {
    }

    public byte[] preprocessData(byte[] bytes, int off, int len)
    {  
        return null;            
    }
    
    public void nowReading(long now)
    {
    }
      
    public void nowPlaying(long now)
    {
    }
      
    public void contentLengthUpdated(long contentLength)
    {
    }       
       
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
