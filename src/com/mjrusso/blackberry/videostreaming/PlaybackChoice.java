package com.mjrusso.blackberry.videostreaming;

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
