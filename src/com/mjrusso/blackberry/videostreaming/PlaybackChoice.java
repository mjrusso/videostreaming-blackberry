package com.mjrusso.blackberry.videostreaming;

import java.io.*;
import javax.microedition.media.*; 
import javax.microedition.media.control.*;
import net.rim.blackberry.api.browser.Browser;
import net.rim.device.api.system.*;
import net.rim.device.api.ui.*;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.ui.container.*;

public abstract class PlaybackChoice
{

    private String _label;
    
    public abstract void play(String url);

    public PlaybackChoice(String label)
    {
        _label = label;
    }
    
    public String toString()
    {
        return _label;
    }

}
