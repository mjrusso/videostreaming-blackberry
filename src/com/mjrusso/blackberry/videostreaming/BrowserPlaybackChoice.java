package com.mjrusso.blackberry.videostreaming;

import net.rim.blackberry.api.browser.Browser;

public class BrowserPlaybackChoice extends PlaybackChoice
{
    public BrowserPlaybackChoice(String label)
    {
        super(label);
    }

    public void play(String url)
    {
        Browser.getDefaultSession().displayPage(url);
    }
}
