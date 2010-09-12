package rimx.media.streaming;

/**
 * Implement this interface and add your implementation using
 * StreamingPlayer.addStreamingPlayerListener(StreamingPlayerListener) to receive events
 * related to the associated StreamingPlayer.
 * @author Shadid Haque
 *
 */
public interface StreamingPlayerListener {
	
	public static final int ERROR_OPENING_CONNECTION = 0;
	public static final int ERROR_CLOSING_CONNECTION = 1;
	public static final int ERROR_DOWNLOADING = 2;
	public static final int ERROR_SEEKING = 3;
	public static final int ERROR_PLAYING_MEDIA = 4;
	
	/**
	 * Invoked only once when the buffer is filled with enough data to meet StreamingPlayer.getInitialBuffer() requirement.
	 * @param available	Number of bytes available in the buffer.
	 */
	public void initialBufferCompleted(long available);
	
	/**
	 * Invoked every time the size of available bytes in the buffer changes.
	 * @param bufferStartsAt The position of "the first byte available" in buffer in the original stream.
	 * @param len	Total number of bytes available in buffer starting with the byte at bufferStartsAt.
	 */
	public void bufferStatusChanged(long bufferStartsAt, long len);
	
	/**
	 * Invoked every time a new chunk is downloaded.
	 * @param totalDownloaded	Amount of data downloaded since StreamingPlayer started.
	 */
	public void downloadStatusUpdated(long totalDownloaded);
	
	/**
	 * Invoked when the feed to the underlying Player is paused due to the fact that 
	 * available&lt;StreamingPlayer.getPauseThreshold(). This is useful to display a status message 
	 * to the user, e.g. "buffering...".
	 * @param available	Number of bytes available in the buffer.
	 */
	public void feedPaused(long available);
	
	/**
	 * Invoked when the feed to the underlying Player is resumed due to the fact that
	 * available&lt;StreamingPlayer.getRestartThreshold(). This is useful to display a status message
	 * to teh user, e.g. "playing..."
	 * @param available	Number of bytes available in the buffer.
	 */
	public void feedRestarted(long available);
		
	
	/** 
	 * Invoked for each chunk of data being downloaded. This allows modification of 
	 * streamed data before it is sent to the underlying Player. (E.g. Decryption, advertisement insertion etc.)
	 * @param bytes	Data that may be pre-processed
	 * @param off	Index of the first byte to preprocess
	 * @param len	Number of bytes to preprocess
	 * @return	pre-processed data. Or simply return null if preprocessing is not required.
	 */
	public byte[] preprocessData(byte[] bytes, int off, int len);	
		
	/**
	 * This method is invoked when the underlying Player's PLayerListener.playerUpdate(Player player, String event, Object eventData)
	 * is called. 
	 * @param event	The event generated as defined by the enumerated types.
	 * @param eventData	The associated event data.
	 */
	public void playerUpdate(String event, Object eventData);
	
		
	/**
	 * Invoked when the contentLength is available or is updated.
	 * @param contentLength	new contentLength.
	 */
	public void contentLengthUpdated(long contentLength);
	
	/**
	 * Triggered when the current position of the player is updated in terms of bytes. Current position of 
	 * the player does not represent what's currently playing but what the player is currently reading.
	 * 
	 * @param now	Current position of the player's read pointer. Between 0 and content-length.
	 */
	public void nowReading(long now);
	
	/**
	 * A convenient event to get notified where the playback is now. The implementation simply uses
	 * getMediaTime on the underlying player to get this information.
	 * @param now	Current position of the playback in microseconds.
	 */
	public void nowPlaying(long now);
	
	/**
	 * Triggered when an exception or error occurs in the private methods of StreamingPlayer
	 * @param errorCode	One of the ERROR_* codes defined in this interface
	 */
	public void streamingError(int errorCode);
	
}
	