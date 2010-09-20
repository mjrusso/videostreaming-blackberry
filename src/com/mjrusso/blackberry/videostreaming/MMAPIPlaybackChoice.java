package com.mjrusso.blackberry.videostreaming;

import net.rim.device.api.ui.*;

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
