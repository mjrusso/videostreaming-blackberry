package rimx.media.streaming;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.Connection;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.file.FileConnection;
import javax.microedition.media.Control;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.protocol.ContentDescriptor;
import javax.microedition.media.protocol.DataSource;
import javax.microedition.media.protocol.SourceStream;

import net.rim.device.api.system.EventLogger;

/**
 * This is the starting point for applications using this library and represents a Player for the 
 * remote media. 

 * @author Shadid Haque
 *
 */
public class StreamingPlayer implements PlayerListener{

        /************* GENERAL CONSTANTS **********/    
        
        /** Connection type: HTTP Connection */
        public static final int CONNECTION_HTTP = 0;
        /** Connection type: HTTPS Connection */
        public static final int CONNECTION_HTTPS = 1;
        /** Connection type: File Connection */
        public static final int CONNECTION_FILE = 2;
        /** Connection type: InputStream Connection */
        public static final int CONNECTION_INPUT_STREAM = 3;
        /** Default     capacity of buffer */
        public static final int DEFAULT_BUFFER_CAPACITY = 4194304;      // 4 MB
        /** Default initialBuffer */
        public static final int DEFAULT_INITIAL_BUFFER = 358000;        // 350 KB       
        /** Default restartThreshold */
        public static final int DEFAULT_RESTART_THRESHOLD = 131072;     // 128 KB
        /** Chunk size for the downloader thread */
        public static final int DOWNLOAD_CHUNK = 1024;  // 1KB
        /** Default buffer leak size */
        public static final int DEFAULT_BUFFER_LEAK = DEFAULT_BUFFER_CAPACITY/3;        // 1KB
        /** Default connectionTimeout */
        public static final int DEFAULT_CONNECTION_TIMEOUT = 6000;      // 1KB
        
        /******************************************/
        
        
        /****** STREAMING PLAYER STATE CONSTANTS ******/
        
        /** Initial state of this StreamingPlayer when initialized using one of its constructors. **/ 
        public static final int UNREALIZED = 0;
        /** StreamingPlayer state when realize() is called */
        public static final int REALIZED = 1;
        /** StreamingPlayer state when prefetch() is called */
        public static final int PREFETCHED = 2;
        /** StreamingPlayer state when start() is called */
        public static final int STARTED = 3;    
        /** StreamingPlayer state when close() is called */
        public static final int CLOSED = 4;     
        
        /***********************************************/
        
        
        /****** Player and Connection resources ******/
        
        /** Reference to the underlying player */
        private Player player;
        /** A reference to this StreamingPlayer */
        private StreamingPlayer streamingPlayer;
        /** Reference to StreamingPlayerListener of this */
        private StreamingPlayerListener listener;
        /** Connection to the media source */
        private Connection connection;  
        /** Type of connection: one of CONNECTION_* constants */
        private int connectionType;
        /** Locator URL of the source media */
        private String locator;
        /** Content type of the source media */
        private String contentType;     
        /** The actual contentLength of the source media. */
        private long contentLength;
        /** Buffer that is used to buffer media content. Data to the underlying Player object flows from this buffer. */        
        private CircularByteBuffer buffer;      
        /** DataSource implementation of this StreamingPlayer */
        private StreamingDataSource dataSource;
        /** InputStream of the source media*/
        private InputStream mediaIStream;
        /** SourceStream implementation. Used to feed data to the player. */
        private StreamingSourceStream feedToPlayer;
        /** An InputStream of the buffer */
        private InputStream bufferIStream;
        /** An OutputStream of the buffer */
        private OutputStream bufferOStream;
        /** A thread that downloads data to the buffer using mediaIStream */
        private Thread downloader;
        /** A flag to stop the StreamingSourceStream.read() call.*/
        private boolean stopRead = false;
        /** A flag to stop the Downloader thread.*/
        private boolean stopDownload = false;
        /** A flag to stop the MediaTime thread.*/
        private boolean stopTimer = false;      
        /** Flag to indicate whether a seek call was initiated by user */
        private boolean userSeek = false;
        /** Indicates that a reconnection is in progress. */
        private boolean reconnectInProgress = false;
        /** Flag to indicate whether a close call was initiated by user */
        private boolean userClose = false;      
        /** A flag to confirm that pending read() calls are terminated */ 
        private boolean readStopped = false;
        /** A flag to confirm that Downloader thread is terminated */
        private boolean downloadStopped = false;
        /** A flag to confirm that MediaTime thread is terminated */ 
        private boolean timerStopped = false;
        /** Indicates that data has been discarded from the buffer */
        private boolean dataDiscarded = false;
        /** Amount of data available in mediaIStream when data is discarded */
        private long availableAtDiscard = 0;
        /** Flag that indicates initial buffering is complete. */
        private boolean initialBufferingComplete = false;
        /** Flag that indicates that all contents of the media source is downloaded */
        private boolean downloadComplete = false;
        /** Total amount of data downloaded. Between 0 and the actual content length. */
        private long totalDownload = 0;
        /** Defines the range of data the buffer is holding at any given time. */
        private long bufferStartsAt = 0;        
        private long bufferEndsAt = 0;  
        /** Current position of the Player's read head */
        private long now = 0;   
        /** Amount of data to buffer before the seek point. */
        private long seekBuffer = 0;
        /** Current state of this StreamingPlayer. One of  UNREALIZED, REALIZED, PREFETCHED, STARTED and CLOSED */      
        private int state;      
        /** A lock to synchronize read operations */
        private Object readLock = new Object();
        /** A lock to synchronize connect and reconnect operations */
        private Object connectionLock = new Object();
        /** A lock to synchronize seek requests initiated by the user.*/
        private Object userSeekLock = new Object();     
        /** Indicates whether debug logging (written in event log) is enabled */
        private boolean eventlogEnabled = false;
        /** Indicates whether debug logging (written in SDCard) is enabled */
        private boolean sdLogEnabled = false;
        /** The level of details to log. 0 is brief, 1 is verbose*/
        private int logLevel = 0;
        /** File location of the log file when sdLogEnabled */
        private FileConnection logFileConn;
        /** Directory location of the log file when sdLogEnabled */
        private FileConnection logDirConn;
        /** DataOutputStream of the log FileConnection*/
        private DataOutputStream logFileConnOut;
        
        
        /*********************************************/
        
        
        /****** Streaming Parameters ******/
                
        /** How much to buffer at the start of the playback */
        private int initialBuffer;      
        /** Minimum bytes required ahead to resume playback from paused state */
        private int restartThreshold;
        /** Total capacity of the buffer */
        private int bufferCapacity;
        /** How much data to lose from the beginning of the buffer when buffer is full and player is reading close to the end of the buffer */
        private int bufferLeakSize;
        /** How long to wait on a connection for more data before reconnecting */
        private int connectionTimeout;
        
        /**********************************/    
        
        /**
         * Creates a new StreamingPlayer as per the locator String. The following protocols are supported:
         *              - http://
         *              - https://
         *              - file://               
         * @param locator       A locator String.
         * @param forcedContentType     content-type of the media stream. Cannot be null for file:/// locators.  
         */
        public StreamingPlayer(String locator, String forcedContentType) {
                // initialize logger.
                EventLogger.register(0x12044bf8d677f8ccL, "StreamingPlayer", EventLogger.VIEWER_STRING);
                
                log(0, "SP.<init>");
                log(0, "\tLocator: " + locator);
                log(0, "\tforcedContentType: "+forcedContentType);
                
                init();         
                setConnectionType(locator);                     
                
                this.contentType = forcedContentType;
        }
        
        
        /**
         * Creates a new StreamingPlayer from an InputStream. StreamingPlayer created from an InputStream
         * is not seekable.
         * @param is    An InputStream.
         * @param forcedContentType     content-type of the media stream. This cannot be null.
         * @throws IllegalArgumentException     If forcedContentType is null.
         */
        public StreamingPlayer(InputStream is, String forcedContentType){
                // initialize logger.
                EventLogger.register(0x12044bf8d677f8ccL, "StreamingPlayer");
                
                log(0, "SP.<init>");
                log(0, "\tLocator: InputStream");
                log(0, "\tforcedContentType: "+forcedContentType);
                
                init();
                connectionType = CONNECTION_INPUT_STREAM;
                
                if(forcedContentType!=null)
                        this.contentType = forcedContentType;
                else
                        throw new IllegalArgumentException("forcedContentType cannot be null");
                mediaIStream = is;
        }
        
        /**
         * Initializes connectionType based on the locator.
         * @param locator       URL of the media resource.
         * @throws IllegalArgumentException     If locator is not one of http://, https:// and file:///
         */
        private void setConnectionType(String locator) {
                this.locator = locator;
                if(locator.toLowerCase().startsWith("http://")){
                        connectionType = CONNECTION_HTTP;
                        log(0, "setConnectionType: HTTP"); 
                }
                else if(locator.toLowerCase().startsWith("https://")){
                        connectionType = CONNECTION_HTTPS;
                        log(0, "setConnectionType: HTTPS");
                }
                else if(locator.toLowerCase().startsWith("file:///")){
                        connectionType = CONNECTION_FILE;
                        log(0, "setConnectionType: FILE");
                }
                else{
                        locator = null;
                        throw new IllegalArgumentException("Locator is not valid.");
                }                       
        }
        
        /**
         * Initializes streaming parameters to default values.
         */
        private void init(){    
                streamingPlayer = this;
                state = UNREALIZED;
                // initialize streaming parameters.
                setInitialBuffer(DEFAULT_INITIAL_BUFFER);               
                setRestartThreshold(DEFAULT_RESTART_THRESHOLD);         
                setBufferCapacity(DEFAULT_BUFFER_CAPACITY);             
                setBufferLeakSize(DEFAULT_BUFFER_LEAK);
                setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT);
                log(0, "Streaming parameters set to defaults");
        }
        
        /**
         * Registers a StreamingPlayerListener for this StreamingPlayer. There can be only one StreamingPlayerListener assigned to a StreamingPlayer at a time.
         * @param listener      A StreamingPlayerListener implementation.
         */
        public void addStreamingPlayerListener(StreamingPlayerListener listener){
                this.listener = listener;
                log(0, "StreamingPlayerListener registered");
        }
        
        /**
         * Removes a StreamingPlayerListener from this StreamingPlayer.
         * @param listener      A StreamingPlayerListener implementation.
         */
        public void removeStreamingPlayerListener(StreamingPlayerListener listener){
                this.listener = null;
                log(0, "StreamingPlayerListener removed");
        }
        
        private void resetFlags(){                                      
                stopRead = false;
                stopDownload = false;           
                stopTimer = false;
                readStopped = false;    
                downloadStopped = false;
                initialBufferingComplete = false;
                downloadComplete = false;               
                userClose = false;
                log(0, "SP.resetFlags()");
        }

        /**
         * Opens connection to the locator URL.
         * Opens InputStream from connection and initializes mediaIStream
         * Initializes StreamingSourceStream feedToPlayer
         */
        private void initConnection() throws IOException{
                try{
                        log(0, "Calling SP.initConnection()..");
                        synchronized(connectionLock){
                                
                                if(getConnectionType()==CONNECTION_HTTP || getConnectionType()==CONNECTION_HTTPS){
                                        connection = (HttpConnection) Connector.open(getLocator(), Connector.READ_WRITE);
                                        log(0, "SP.initConnection() - Connection opened: " + getLocator());
                                        ((HttpConnection)connection).setRequestProperty("Range", "bytes=" + bufferStartsAt + "-");
                                        log(0, "SP.initConnection() - Range header set: " + "bytes=" + bufferStartsAt + "-");
                                        mediaIStream = ((HttpConnection)connection).openInputStream();
                                        log(0, "SP.initConnection() - mediaIStream:InputStream opened");
                                        if (contentType == null) {
                                                setContentType(((HttpConnection)connection).getType());                                 
                                        }                       
                                        if(contentLength==0){
                                                contentLength = ((HttpConnection)connection).getLength();                               
                                                log(0, "SP.initConnection() - contentLength set: " + contentLength);
                                        }
                                } else if(getConnectionType()==CONNECTION_FILE){
                                        connection = (FileConnection) Connector.open(getLocator(), Connector.READ);
                                        log(0, "SP.initConnection() - Connection opened: " + getLocator());
                                        mediaIStream = ((FileConnection)connection).openInputStream();
                                        log(0, "SP.initConnection() - mediaIStream:InputStream opened");                        
                                        if(contentLength==0){
                                                contentLength = ((FileConnection)connection).fileSize();                                
                                                log(0, "SP.initConnection() - contentLength set: " + contentLength);
                                        }
                                } else if(getConnectionType()==CONNECTION_INPUT_STREAM){                        
                                        if(contentLength==0){
                                                contentLength = -1;                             
                                                log(0, "SP.initConnection() - contentLength set: -1");
                                        }
                                }                       
                        }
                } catch(IOException e){
                        notifyStreamingError(StreamingPlayerListener.ERROR_OPENING_CONNECTION);
                        throw e;
                }
        }
        
        /**
         * Used to reconnect and resume download in case download encounters an error.
         */
        private void resumeDownload(){
                log(0, "Calling SP.resumeDownload()..");
                synchronized(connectionLock){
                        try{                            
                                streamingPlayer.closeConnection();
                                stopDownload = false;
                                long oldBufferStartsAt = bufferStartsAt;
                                bufferStartsAt = bufferEndsAt;  // re-setting bufferStartsAt because this is sent as the range-header.
                                streamingPlayer.initConnection();
                                bufferStartsAt = oldBufferStartsAt;
                                dataSource.start(); 
                                reconnectInProgress = false;
                                log(0, "SP.resumeConnection() - download resumed.");
                        } catch(Throwable t){
                                reconnectInProgress = false;
                        }
                }
        }
         
        /**
         * Closes connection
         */
        private void closeConnection() throws IOException{
                log(0, "Calling SP.closeConnection()..");
                synchronized(connectionLock){                                   
                        stopDownload = true;
                        
                        if (mediaIStream != null) {
                                mediaIStream.close();   
                                mediaIStream = null;
                        }
                        if(connection!=null){
                                connection.close();
                                connection = null;
                        }               
                        log(0, "SP.closeConnection() - Successful");
                }
                
        }
        /**
         * Creates the underlying Player object and calls realize() on it. It also puts this StreamingPlayer in the 
         * REALIZED state. Calling it when StreamingPlayer is not in UNREALIZED state has no effect. Also initializes
         * the buffer of this StreamingPlayer.
         * @throws IOException  Thrown by underlying Player while calling Manager.createPlayer().
         * @throws MediaException       Thrown by underlying Player while calling Manager.createPlayer().
         */
        public void realize() throws IOException, MediaException{       
                log(0, "Calling realize()..");
                seekBuffer = initialBuffer/3;
                resetFlags();
                if(buffer==null)
                        buffer = new CircularByteBuffer(bufferCapacity, true);
                if(getState()==UNREALIZED){
                        dataSource = new StreamingDataSource(locator);
                        player = Manager.createPlayer(dataSource);
                        
                        player.addPlayerListener(this);                 
                        bufferIStream.reset();
                        bufferIStream.mark(buffer.getSize()-2);
                        
                        if(player!=null){                       
                                player.realize();
                                state = REALIZED;
                                log(0, "SP.realize() - State: REALIZED");                               
                        }                       
                         
                }               
        }
        
        /**
         * Puts this StreamingPlayer in the PREFETCHED state. 
         * If this StreamingPlayer is in REALIZED state, simply calls prefetch() on the underlying Player. 
         * If this StreamingPlayer is in UNREALIZED state, calls StreamingPlayer.realize() and then Player.prefetch(). 
         * Otherwise, this method does nothing.
         * @throws MediaException       Thrown by Player.prefetch() or StreamingPlayer.realize().
         * @throws IOException  Thrown by StreamingPlayer.realize().
         */
        public void prefetch() throws MediaException, IOException{
                log(0, "Calling prefetch()..");
        
                if(getState()==UNREALIZED){
                        realize();
                        if(player!=null){
                                player.prefetch();
                                state = PREFETCHED;
                                log(0, "SP.prefetch() - State: PREFETCHED");
                        }                       
                } else if(getState()==REALIZED){
                        if(player!=null){
                                player.prefetch();
                                state = PREFETCHED;
                                log(0, "SP.prefetch() - State: PREFETCHED");
                        }                       
                }       
        }
        
        /**
         * Starts the playback by calling Player.start() and puts this StreamingPlayer in STARTED state. 
         * If StreamingPlayer is in UNREALIZED state, calls StreamingPlayer.realize(), StreamingPlayer.prefetch() and Player.start().
         * If StreamingPlayer is in REALIZED state, calls StreamingPlayer.prefetch() and Player.start().
         * If StreamingPlayer is in PREFETCHED state, calls Player.start().
         * Otherwise, the call is ignored.
         * 
         * @throws MediaException Thrown by StreamingPlayer.realize(), StreamingPlayer.prefetch() or Player.start()
         * @throws IOException Thrown by StreamingPlayer.realize()
         */
        public void start() throws MediaException, IOException{
                log(0, "Calling start()..");
        
                if(getState()==UNREALIZED){
                        realize();
                        prefetch();                     
                        if(player!=null){
                                player.start();
                                state = STARTED;
                                log(0, "SP.start() - State: STARTED");
                        }
                } else if(getState()==REALIZED){                        
                        prefetch();                     
                        if(player!=null){
                                player.start();
                                state = STARTED;
                                log(0, "SP.start() - State: STARTED");
                        }
                } else if(getState()==PREFETCHED){                      
                        if(player!=null){
                                player.start();
                                state = STARTED;
                                log(0, "SP.start() - State: STARTED");
                        }
                }
                new MediaTimeThread().start();
                notifyContentLengthUpdated(contentLength);
        }
        
        /**
         * Pauses the playback by calling Player.stop() and puts this StreamingPlayer in PREFETCHED state.
         * If StreamingPlayer is in STARTED state, calls Player.stop()
         * Otherwise, the call is ignored.  
         * 
         * @throws MediaException       Thrown by Player.stop().
         */
        public void stop() throws MediaException{
                log(0, "Calling stop()..");             
                if(player!=null && player.getState()==Player.STARTED){
                        player.stop();
                        state = PREFETCHED;
                        log(0, "SP.stop() - State: PREFETCHED");
                }               
        }       
        
        /**
         * Closes this StreamingPlayer by closing 
         *              the connection to the media resource.
         *              the underlying Player object by calling Player.close().
         * This method blocks until all the threads (DownloadThread and read()) are safely terminated. 
         * @throws MediaException thrown by Player.stop();                
         */
        public void close() throws MediaException{
                state = CLOSED;
                log(0, "Calling SP.close()..");
                userClose = true;
                stopRead = true;
                stopDownload = true;
                stopTimer = true;
                
                if(player!=null){
                        player.stop();                  
                        player.close();
                        player=null;
                }
                
                if(dataSource!=null){
                        dataSource.disconnect();
                        dataSource=null;                        
                }               
                
                log(0, "SP.close() - All resources cleaned.");
        
        }
        
        /**
         * Deallocates this StreamingPlayer. 
         * If StreamingPlayer is in PREFETCHED state, calls Player.deallocate() and also moves this StreamingPlayer to REALIZED state.
         * Otherwise, the call is ignored. 
         * @throws      IllegalStateException   Thrown by underlying Player if current state is not PREFETCHED
         */
        public void deallocate() throws IllegalStateException{
                log(0, "Calling deallocate()..");
                                
                if(player!=null){
                        player.deallocate();
                        state = REALIZED;
                        log(0, "SP.deallocate() - State: REALIZED");
                }               
                log(0, "SP.deallocate() - Player is null.");                    
        }
        
        /**
         * Returns the content type of the media stream. 
         * @return
         */
        public String getContentType(){
                log(0, "SP.getContentType() - " + contentType);
                return contentType;
        }
        
        /**
         * Sets the contentType. Can only be called in the UNREALIZED state.
         * @param contentType   The contentType to set.
         * @throws IllegalStateException        If this StreamingPlayer is NOT in UNREALIZED state.
         */
        public void setContentType(String contentType) throws IllegalStateException{
                if(getState()!=UNREALIZED){
                        log(0, "FAILED! SP.setContentType() - " + contentType);
                        throw new IllegalStateException("Can only be called in UNREALIZED state");                      
                }
                else{
                        this.contentType = contentType;
                        log(0, "SP.setContentType() - " + contentType);
                }
        }
        
        /**
         * Gets the duration of the media. The value returned is the media's duration when played at the default rate.
         * If the duration cannot be determined (for example, the Player is presenting live media) getDuration returns TIME_UNKNOWN.
         * 
         * @return      The duration in microseconds or TIME_UNKNOWN.
         * @throws      IllegalStateException - Thrown if the Player is in the CLOSED state. Or if the StreamingPlayer is in UNREALIZED state.
         */
        public long getDuration() throws IllegalStateException{
                if(getState()==UNREALIZED){
                        log(0, "FAILED! SP.getDuration()");
                        throw new IllegalStateException("Cannot be called in UNREALIZED state");
                }       
                long duration = player.getDuration();
                log(0, "SP.getDuration() - " + duration);
                return duration;
        }
        
        /**
         * Gets the length of the available data in the buffer.
         * @return      the length of the available data in the buffer.
         * @throws IllegalStateException        If StreamingPlayer is in UNREALIZED state. 
         */
        public long getBufferContentLength() throws IllegalStateException{              
                if(getState()==UNREALIZED){
                        log(0, "FAILED! SP.getBufferContentLength()");
                        throw new IllegalStateException("Cannot be called in UNREALIZED state");
                }               
                long bufferContentLength = bufferEndsAt - bufferStartsAt + 1;
                log(0, "SP.getBufferContentLength() - " + bufferContentLength);
                return bufferContentLength;
        }
        
        /**
         * Gets the content length of the media source.
         * @return      the content length of the media source.
         * @throws IllegalStateException        If StreamingPlayer is in UNREALIZED state.
         */
        public long getContentLength() throws IllegalStateException{    
                if(getState()==UNREALIZED){
                        log(0, "FAILED! SP.getContentLength()");
                        throw new IllegalStateException("Cannot be called in UNREALIZED state");
                }       
                log(0, "SP.getContentLength() - " + contentLength);
                return contentLength;
        }
        
        /**
         * Sets the Player's media time. 
         * For some media types, setting the media time may not be very accurate. The returned value will indicate the actual media time set.
         * 
         * Setting the media time to negative values will effectively set the media time to zero. Setting the media time to beyond the duration of the media will set the time to the end of media.
         * 
         * There are some media types that cannot support the setting of media time. Calling setMediaTime will throw a MediaException in those cases.
         * 
         * @param       microseconds    The new media time in microseconds.
         * @return      The actual media time set in microseconds.
         * @throws      IllegalStateException - Thrown if the Player is in the UNREALIZED or CLOSED state.
         * @throws      MediaException - Thrown if the media time cannot be set.         * 
         */
        public long setMediaTime(long microseconds) throws MediaException, IllegalStateException{       
                log(0, "Calling SP.setMediaTime("+microseconds+")..");
                if(getState()==UNREALIZED){
                        log(0, "FAILED! SP.setMediaTime()");
                        throw new IllegalStateException("Cannot be called in UNREALIZED state");
                }                       
                while(userSeek){
                        synchronized(userSeekLock){
                                if(!userSeek){
                                        userSeek = true;
                                        break;
                                }
                        }
                        try { Thread.sleep(100); } catch (Throwable e) { }                      
                }
                userSeek = true;
                synchronized(connectionLock){                   
                        long time = player.setMediaTime(microseconds);
                        log(0, "SP.setMediaTime("+microseconds+") - " + time);
                        return time;
                }
        }
        
        /**
         * Gets this Player's current media time. If the media time cannot be determined, getMediaTime returns TIME_UNKNOWN.
         * 
         * @return      The current media time in microseconds or TIME_UNKNOWN.
         * @throws      IllegalStateException   Thrown if the Player is in the CLOSED state. Or if the StreamingPlayer is in UNREALIZED state.
         */
        public long getMediaTime() throws IllegalStateException{
                if(getState()==UNREALIZED){
                        log(0, "FAILED! SP.getMediaTime()");
                        throw new IllegalStateException("Cannot be called in UNREALIZED state");
                }       
                long time = player.getMediaTime();
                log(0, "SP.getMediaTime() - " + time);
                return time;
        }
        
        /**
         * Obtain the object that implements the specified Control interface.
         * 
         * If the specified Control interface is not supported then null is returned.
         * 
         * If the Controllable supports multiple objects that implement the same specified Control interface, only one of them will be returned. To obtain all the Control's of that type, use the getControls method and check the list for the requested type.
         * @param       controlType     the class name of the Control. The class name should be given either as the fully-qualified name of the class; or if the package of the class is not given, the package javax.microedition.media.control is assumed.
         * @return      the object that implements the control, or null.
         * @throws      IllegalArgumentException        Thrown if controlType is null.
         * @throws      IllegalStateException   Thrown if getControl is called in a wrong state. See Player for more details.
         */
        public Control getControl(String controlType){
                return player.getControl(controlType);
        }
        
        /**
         * Obtain the collection of Controls from the object that implements this interface.
         * 
         * The list of Control objects returned will not contain any duplicates. And the list will not change over time.
         * 
         * If no Control is supported, a zero length array is returned.
         * @return      the collection of Control objects.
         */
        public Control[] getControls(){
                return player.getControls();
        }
        
        /**
         * Gets the current state of this StreamingPlayer.  
         * @return      One of  UNREALIZED, REALIZED, PREFETCHED, STARTED and CLOSED. 
         */
        public int getState(){
                log(0, "SP.getState() - " + state);
                return state;
        }
                
        /**
         * PlayerListener implementation.
         */
        public void playerUpdate(Player player, String event, Object eventData) {
                log(0, "SP.playerUpdate() - " + event + "[" + eventData + "]");
                if(listener!=null)
                        listener.playerUpdate(event, eventData);
                
                if(event.equalsIgnoreCase(PlayerListener.ERROR)){
                        log(0, "SP.playerUpdate() - Closing StreamingPlayer due to an error");
                        if(listener!=null)
                                listener.streamingError(StreamingPlayerListener.ERROR_PLAYING_MEDIA);
                        try{ streamingPlayer.close(); } catch(Throwable t){ }
                }
        }

        /**
         * Gets the locator url of the media source.
         * @return      url of the media source.
         */     
        public String getLocator() {
                log(0, "SP.getLocator() - " + locator);
                return locator;
        }
        
        /**
         * Gets the initial buffer size of this StreamingPlayer. 
         * @return      initial buffer size of this StreamingPlayer
         */
        public int getInitialBuffer() {
                log(0, "SP.getInitialBuffer() - " + initialBuffer);
                return initialBuffer;
        }

        /**
         * Sets the initial buffer size of this StreamingPlayer. 
         * @param initialBuffer Size of initialBuffer. Must be less than bufferCapacity and bufferLeakSize and greater than restartThreshold.   
         */
        public void setInitialBuffer(int initialBuffer) {
                if(getState()!=UNREALIZED){
                        log(0, "FAILED! SP.setInitialBuffer()");
                        throw new IllegalStateException("Can only be called in UNREALIZED state");
                }       
                log(0, "SP.setInitialBuffer() - " + initialBuffer);
                this.initialBuffer = initialBuffer;             
        }


        /**
         * Gets the restartThreshold: Minimum bytes required ahead to resume playback from paused state.
         * @return      Minimum bytes required ahead to resume playback from paused state.
         */
        public int getRestartThreshold() {
                log(0, "SP.getRestartThreshold() - " + restartThreshold);
                return restartThreshold;
        }

        /**
         * Sets the restartThreshold: Minimum bytes required ahead to resume playback from paused state
         * @param restartThreshold      new restartThreshold value. Must be less than bufferCapacity, bufferLeakSize, initialBuffer and greater than pauseThreshold.
         */
        public void setRestartThreshold(int restartThreshold) {         
                if(restartThreshold<80000){     // Set the minimum allowed value
                        this.restartThreshold = 80000;
                        log(0, "SP.setRestartThreshold() [setting default] - " + 80000);
                } else{
                        this.restartThreshold = restartThreshold;
                        log(0, "SP.setRestartThreshold() - " + restartThreshold);
                }
        }

        /**
         * Sets the amount of data the buffer is allowed to lose to make space for new data for download.
         * The buffer is designed to hold previously downloaded data as long as possible. When the player
         * reaches the end of the buffer to read for more and the buffer is full, some data must be discarded 
         * to make space for more data to download unless end of the stream is reached. Data is discarded 
         * from the beginning of the buffer.
         *  
         * @param size  The amount of data to discard. Must be less than bufferCapacity and greater than initialBuffer and restartThreshold.  
         */
        public void setBufferLeakSize(int size){
                this.bufferLeakSize = size;
        }
        
        /**
         * Gets the amount of data the buffer is allowed to lose to make space for new data for download.
         * The buffer is designed to hold previously downloaded data as long as possible. When the player
         * reaches the end of the buffer to read for more and the buffer is full, some data must be discarded 
         * to make space for more data to download unless end of the stream is reached. Data is discarded 
         * from the beginning of the buffer.
         * @return      How much data is discarded.
         */
        public int getBufferLeakSize(){
                return bufferLeakSize;
        }
        
        /**
         * Gets the capacity of the buffer.
         * @return      Capacity of the buffer.
         */
        public int getBufferCapacity() {
                log(0, "SP.getBufferCapacity() - " + bufferCapacity);
                return bufferCapacity;
        }
        
        /**
         * Sets the buffer capacity. Must be set before the player is realized. 
         * @param size  Size of the buffer. Must be greater than initialBuffer, bufferLeakSize, pauseThreshold or restartThreshold 
         *  
         */
        public void setBufferCapacity(int size){                
                bufferCapacity = size;          
        }

        /**
         * Increases the size of the buffer by a percent factor of the current bufferCapacity.
         * @param percent       Amount of increase as a percentage of the current bufferCapacity. E.g. a factor of 50 will increase a 1000KB buffer to 1500KB.
         */
        public void increaseBufferCapacity(int percent) {
                if(percent < 0){ 
                        log(0, "FAILED! SP.setBufferCapacity() - " + percent);
                        throw new IllegalArgumentException("Increase factor must be positive..");
                }
                synchronized(readLock){
                        synchronized(connectionLock){                           
                                synchronized(userSeekLock){
                                        synchronized(mediaIStream){                             
                                                log(0, "SP.setBufferCapacity() - " + percent);          
                                                buffer.resize();
                                                this.bufferCapacity = buffer.getSize();
                                        }
                                }
                        }
                }
        }
        
        /**
         * Gets the connectionTimeout value
         * @return      connectionTimeout value
         */
        public int getConnectionTimeout(){
                return connectionTimeout;
        }
        
        /**
         * Sets the connectionTimeout. Since we are using a blocking buffer i.e. download is blocked until player is done with reading a full buffer,
         * when the buffer is freed up to download more data, the connection might become broken by that time. An exception will be eventually thrown
         * and connection will be re-initialized. However, the exception might be very delayed and pause the playback for that period. To ensure 
         * smooth playback, this connectionTimeout value determines how long this StreamingPlayer will wait for a connection before forcefully
         * re-initializing the connection.
         * @param timeout
         */
        public void setConnectionTimeout(int timeout){
                this.connectionTimeout = timeout;
        }

        /**
         * Gets the connectionType of this StreamingPlayer.
         * @return      One of CONNECTION_FILE|CONNECTION_HTTP|CONNECTION_HTTPS|CONNECTION_INPUT_STREAM.
         */
        public int getConnectionType() {
                log(0, "SP.getConnectionType() - " + connectionType);
                return connectionType; 
        }
        /****** End of Getters and Setters ******/
        
        /**
         * Logs a message in the eventlog of the device.
         * @param msg   Message to log.
         */
        private void log(int level, String msg){
                if(level<=logLevel){
                        if(eventlogEnabled){
                                System.out.println(msg);
                                EventLogger.logEvent(0x12044bf8d677f8ccL, msg.getBytes(), EventLogger.ALWAYS_LOG);
                        }
                        if(sdLogEnabled && logFileConn!=null && logFileConnOut!=null){
                                try { logFileConnOut.write((msg+"\n").getBytes()); } catch (Throwable t) { }
                        }
                }
        }
        
        /**
         * Enables logging on the Eventlog as well as on the SDCard
         * @param eventLog      Logging in the Eventlog is on if true.
         * @param sdLog         Logging on the SDCard is on if true.
         * @param sdURL         URL of the file on the SDCard 
         */
        public void enableLogging(boolean eventLog, boolean sdLog){
                eventlogEnabled = eventLog;
                if(sdLog){
                        sdLogEnabled = true;
                                
                        try{                            
                                logDirConn = (FileConnection)Connector.open("file:///SDCard/StreamingPlayer/", Connector.READ_WRITE);
                                if(logDirConn!=null && !logDirConn.exists()){
                                        logDirConn.mkdir();                                             
                                }
                                if(logDirConn!=null)
                                        logDirConn.close();
                                
                                String sdURL = "file:///SDCard/StreamingPlayer/LOG_"+System.currentTimeMillis()+".txt";
                                logFileConn = (FileConnection)Connector.open(sdURL, Connector.READ_WRITE);
                                if(logFileConn!=null && !logFileConn.exists()){
                                        logFileConn.create();
                                } 
                                logFileConnOut = logFileConn.openDataOutputStream();
                                
                        } catch(Throwable t){
                                logFileConn = null;                     
                                logFileConnOut = null;
                        }
                        
                }
        }
        
        /**
         * Sets the details level for logging. 
         * @param level The level of details. 0 is brief, 1 is verbose.
         */
        public void setLogLevel(int level){
                this.logLevel = level;
        }
        
        /** Private methods to notify StreamingPlayerListener **/
        
        
        private void notifyInitialBufferCompleted(long available){
                if(listener!=null){
                        listener.initialBufferCompleted(available);
                }
        }
        
        private void notifyBufferStatusChanged(long bufferStartsAt, long len){
                if(listener!=null){
                        listener.bufferStatusChanged(bufferStartsAt, (bufferEndsAt - bufferStartsAt + 1));
                }
        }
        
        private void notifyDownloadStatusUpdated(long totalDownloaded){
                if(listener!=null){
                        listener.downloadStatusUpdated(totalDownloaded);
                }
        }
        
        private void notifyFeedPaused(long available){
                if(listener!=null){
                        listener.feedPaused(available);
                }
        }
        
        private void notifyFeedRestarted(long available){
                if(listener!=null){
                        listener.feedRestarted(available);
                }
        }
        
        private void notifyContentLengthUpdated(long contentLength){
                if(listener!=null){
                        listener.contentLengthUpdated(contentLength);
                }
        }
        
        private byte[] notifyPreprocessData(byte[] bytes, int off, int len){
                if(listener!=null){
                        return listener.preprocessData(bytes, off, len);
                } else{
                        return null;
                }               
        }       
        
        private void notifyNowReading(long position){
                if(listener!=null){
                        listener.nowReading(position);
                }
        }
        
        private void notifyNowPlaying(long position){
                if(listener!=null){
                        listener.nowPlaying(position);
                }
        }
        
        private void notifyStreamingError(int errorCode){
                if(listener!=null){
                        listener.streamingError(errorCode);
                }
        }
        
        /*******************************************************/
        
        
        /************************* DataSource Implementation *************************/
        
        private class StreamingDataSource extends DataSource{           
                
                /**
                 * Constructs a new StreamingDataSource.
                 * @param locator       URL of the media source.
                 */
                public StreamingDataSource(String locator){                     
                        super(locator);
                        log(0, "SDS <init>: " + locator);
                }
                
                /**
                 * Opens connection to the locator URL.
                 * Opens InputStream from connection and initializes mediaIStream
                 * Initializes StreamingSourceStream feedToPlayer
                 */
                public void connect() throws IOException {      
                        log(0, "Calling SDS.connect()...");
                        initConnection();
                        
                        if(getConnectionType()==CONNECTION_HTTP || getConnectionType()==CONNECTION_HTTPS){                              
                                feedToPlayer = new StreamingSourceStream();
                                log(0, "SDS.connect() - feedToPlayer:StreamingSourceStream initialized");                               
                        } else if(getConnectionType()==CONNECTION_FILE){                                
                                feedToPlayer = new StreamingSourceStream();
                                log(0, "SDS.connect() - feedToPlayer:StreamingSourceStream initialized");                               
                        } else if(getConnectionType()==CONNECTION_INPUT_STREAM){                                                                
                                feedToPlayer = new StreamingSourceStream();
                                log(0, "SDS.connect() - feedToPlayer:StreamingSourceStream initialized");                               
                        }
                }

                /**
                 * Closes and cleans up connection, mediaIStream and feedToPlayer.
                 */
                public void disconnect(){
                        log(0, "Calling SDS.disconnect()...");                  
                        try{                            
                                if(logFileConn!=null){
                                        logFileConn.close();
                                }
                                stopRead = true;
                                stopDownload = true;
                                stopTimer = true;
                                streamingPlayer.closeConnection();
                                
                                if(feedToPlayer!=null){
                                        feedToPlayer.close();
                                        feedToPlayer = null;
                                }
                                
                                log(0, "SDS.disconnect() - Successful");
                        } catch(Throwable t){
                                log(0, "FAILED! SDS.disconnect() - " + t.toString());
                        }
                }

                /**
                 * Get a sting that describes the content-type of the media that the source is providing.
                 * @return      The name that describes the media content. Returns null if the content is unknown.              
                 */
                public String getContentType() {
                        log(0, "SDS.getContentType() - " + contentType);
                        return contentType;
                }

                /**
                 * Get the collection of streams that this source manages. The collection of streams is entirely 
                 * content dependent. The MIME type of this DataSource provides the only indication of what 
                 * streams may be available on this connection. Only one SourceStream is supported by RIM.
                 */
                public SourceStream[] getStreams() {
                        log(0, "SDS.getStreams()");
                        return new SourceStream[] {feedToPlayer};
                }

                /**
                 * Starts the downloader thread.
                 */
                public void start() throws IOException {                        
                        downloader = new Downloader();
                        downloader.start();             
                }

                /**
                 * Sets stop flag to true which should stop streaming.
                 */
                public void stop() throws IOException {
                        log(0, "SDS.stop() - stop flag set");
                        stopRead = true;
                        stopDownload = true;
                        stopTimer = true;
                }

                public Control getControl(String controlType) {
                        log(0, "SDS.getControl()");
                        return null;
                }

                public Control[] getControls() {
                        log(0, "SDS.getControls()");
                        return null;
                }
                
        }
        
        /************************* End of DataSource Implementation *************************/
        
        
        /************************* SourceStream Implementation *************************/
        
        private class StreamingSourceStream  implements SourceStream{           
                /**
                 * Initializes bufferIStream with InputStream of buffer.
                 */
                public StreamingSourceStream(){
                        log(0, "SSS<init>");
                        bufferIStream = buffer.getInputStream();                        
                        bufferOStream = buffer.getOutputStream();
                        log(0, "SSS<init> - bufferIStream:InputStream and bufferOStream:OutputStream opened");
                }               
                
                /**
                 * Get the current type for this stream.
                 */
                public ContentDescriptor getContentDescriptor() {
                        log(0, "SSS.getContentDescriptor() - " + contentType);
                        return new ContentDescriptor(contentType);
                }

                /**
                 * Get the size of a "logical" chunk of media data from the source.
                 */
                public int getTransferSize() {
                        log(0, "SSS.getTransferSize() - " + 58000);
                        return 58000;
                }

                /**
                 * Gets data from buffer and returns to player as requested.
                 */
                public int read(byte[] b, int off, int len) throws IOException {                        
                        log(1, "Calling SSS.read("+len+")..");
                        readStopped = false;
                        
                        synchronized (readLock) {
                                if (stopRead) { // stop flag is set. Return -1
                                        log(1, "SSS.read() - " + -1);
                                        readStopped = true;     // set readStopped to notify waiting threads                            
                                        return -1;                                      
                                }

                                int readCount = 0;                              
                                int available = 0;

                                boolean restart_pause = false;  
                                
                                for (;;) {
                                        if (stopRead) { // stop flag is set. Return -1
                                                log(1, "SSS.read() - " + -1);
                                                readStopped = true;     // set readStopped to notify waiting threads
                                                return -1;
                                        }
                                        
                                        available = buffer.getAvailable();
                                        
                                        if (downloadComplete) {                 
                                                notifyFeedRestarted(available);
                                                if(available<=0){       // Since downloadComplete, available<=0 means end of stream
                                                        log(1, "SSS.read() - EOF reached.");
                                                        readStopped = true;     // set readStopped to notify waiting threads                                                    
                                                        return -1;
                                                }
                                                else if(len>available){ // we have less than len bytes left in the stream. return whatever is left. 
                                                        readCount = bufferIStream.read(b, off, available);

                                                        log(1, "SSS.read() - " + readCount);
                                                        
                                                        now += readCount;       
                                                        notifyNowReading(now);
                                                        
                                                        readStopped = true;     // set readStopped to notify waiting threads
                                                        return readCount;
                                                } else{ // we have at least len bytes left in the stream. return len bytes.
                                                        readCount = bufferIStream.read(b, off, len);

                                                        log(1, "SSS.read() - " + readCount);
                                                        
                                                        now += readCount;       
                                                        notifyNowReading(now);
                                                        
                                                        readStopped = true;     // set readStopped to notify waiting threads
                                                        return readCount;
                                                }                                                       
                                                
                                        } else if (initialBufferingComplete) {
                                                if (restart_pause && available > restartThreshold) {    //feed was paused but since then we have downloaded enough to resume.                                                   
                                                        restart_pause = false;
                                                        log(1, "SSS.read() - restart_pause cleared!");
                                                        notifyFeedRestarted(available);

                                                        readCount = bufferIStream.read(b, off, len);

                                                        log(1, "SSS.read() - " + readCount);
                                                        
                                                        now += readCount;               
                                                        notifyNowReading(now);
                                                        
                                                        readStopped = true;     // set readStopped to notify waiting threads
                                                        return readCount;
                                                } else if (!restart_pause && available > len+3) {       // We have what is needed but might need to set the restart_pause                                               
                                                        notifyFeedRestarted(available);
                                                        readCount = bufferIStream.read(b, off, len);
                                                        
                                                        log(1, "SSS.read() - " + readCount);
                                                        
                                                        now += readCount;       
                                                        notifyNowReading(now);
                                                        
                                                        readStopped = true;
                                                        return readCount;                                                       
                                                } else if (!restart_pause) {    // We dont have enough. Set the restart_pause                                                   
                                                        restart_pause = true;
                                                        notifyFeedPaused(available);                                            
                                                        log(1, "SSS.read() - restart_pause set!");
                                                } else{ // restart_pause is already set and we dont have enough. Discard some data from the beginning of the buffer (if necessary) to write/download more data to the buffer. Sleep a bit before looping back.                                                  
                                                        if(restart_pause && buffer.getSpaceLeft()<=DOWNLOAD_CHUNK){                     
                                                                synchronized(connectionLock){
                                                                        log(0, "SSS.read() - discarding data..");
                                                                        availableAtDiscard = mediaIStream.available();
                                                                        log(0, "SSS.read() - mediaIStream.available(): " + availableAtDiscard);
                                                                        bufferIStream.reset();                                  
                                                                        long discardCount = bufferIStream.skip(bufferLeakSize);
                                                                        bufferStartsAt += discardCount;
                                                                        bufferIStream.mark(buffer.getSize()-2);
                                                                        long skipToNow = bufferIStream.skip(now-bufferStartsAt);
                                                                        dataDiscarded = true;
                                                                        log(0, "SSS.read() - discarded " + discardCount);
                                                                        notifyBufferStatusChanged(bufferStartsAt, buffer.getSize()-buffer.getSpaceLeft());                                                                                                              
                                                                }
                                                        }                                                       
                                                        log(0, "SSS.read() - feed paused: sleeping..");                                                         
                                                        try{Thread.sleep(500);} catch(Throwable t){}            
                                                }
                                        } else {        // initial buffering is not complete yet. So sleep a bit and loop back                                          
                                                log(0, "SSS.read() - !bufferingComplete: sleeping.." + "Available: " + available);                                                                              
                                                try{ Thread.sleep(500);} catch (Throwable e) {}
                                        }
                                }
                        }
                }

                
                /** 
                 * Get the size in bytes of the content on this stream.  
                 */
                public long getContentLength() {
                        if(connectionType==CONNECTION_HTTP || connectionType==CONNECTION_HTTPS){                                        
                                log(0, "SSS.getContentLength() - " + contentLength);
                                return contentLength;
                                
                        } else if(connectionType==CONNECTION_INPUT_STREAM){     // return -1 because contentLength is not known for InputStreams.
                                log(0, "SSS.getContentLength() - " + -1);
                                return -1;      
                        } else if(connectionType==CONNECTION_FILE){     // return true length because we are playing a local resource and can seek forward. 
                                try {
                                        long length = ((FileConnection)connection).fileSize();
                                        log(0, "SSS.getContentLength() - " + length);
                                        return length;
                                } catch (IOException e) {
                                        log(0, "!IOException! in SSS.getContentLength() - -1");
                                        return -1;
                                }
                        } else{
                                log(0, "SSS.getContentLength() - " + -1);
                                return -1;
                        }
                } 

                /**
                 * Gets the seek type of the connection. Returns either NOT_SEEKABLE (for InputStream based StreamingPlayer) or 
                 * RANDOM_ACCESSIBLE (for file and http/https connection). 
                 */
                public int getSeekType() {
                        if(getConnectionType()==CONNECTION_HTTP || getConnectionType()==CONNECTION_HTTPS){
                                if(contentLength>0){
                                        log(0, "SSS.getSeekType() - " + "RANDOM_ACCESSIBLE");
                                        return RANDOM_ACCESSIBLE;                       
                                } else{
                                        log(0, "SSS.getSeekType() - " + "NOT_SEEKABLE");
                                        return NOT_SEEKABLE;
                                }
                        } else if(getConnectionType()==CONNECTION_FILE){
                                log(0, "SSS.getSeekType() - " + "RANDOM_ACCESSIBLE");
                                return RANDOM_ACCESSIBLE;
                        } else if(getConnectionType()==CONNECTION_INPUT_STREAM){
                                log(0, "SSS.getSeekType() - " + "NOT_SEEKABLE");
                                return NOT_SEEKABLE;
                        } else{
                                log(0, "SSS.getSeekType() - " + "NOT_SEEKABLE");
                                return NOT_SEEKABLE;
                        }                        
                }
                
                /**
                 * Seek to the specified point within the buffer. This is called by the underlying Player.
                 */
                public long seek(long where) {  
                        log(0, "Calling SSS.seek("+where+")..");
                        if(where<0){
                                where=0;
                        }
                        
                        synchronized(readLock){
                                synchronized(connectionLock){
                                        if(getConnectionType()==CONNECTION_HTTP || getConnectionType()==CONNECTION_HTTPS || getConnectionType()==CONNECTION_INPUT_STREAM){      
                                                if(where>=bufferStartsAt && where<=bufferEndsAt){       // seek if seek point is within what the buffer holds now
                                                        try{
                                                                bufferIStream.reset();  // reset the buffer stream
                                                                bufferIStream.mark(buffer.getSize()-2); // re-mark to ensure data persistence as long as possible
                                                                
                                                                long actualSkip = bufferIStream.skip(where-bufferStartsAt);     // skip to the seek location                                            
                                                                now = bufferStartsAt+actualSkip;        // update now   
                                                                notifyNowReading(now);
                                                                log(0, "SSS.seek("+where+") - " + now);
                                                                userSeek = false;
                                                        } catch(Throwable t){
                                                                notifyStreamingError(StreamingPlayerListener.ERROR_SEEKING);
                                                        }
                                                        return now;
                                                } else if((where<bufferStartsAt || where>bufferEndsAt) && userSeek && getConnectionType()!=CONNECTION_INPUT_STREAM){    // User initiated seek..
                                                        try{                                                            
                                                                now = where;
                                                                notifyNowReading(now);
                                                                log(0, "SSS.seek("+where+") [userSeek]");
                                                                stopDownload = true;    // stop Download Thread
                                                                buffer.clear(); // Clear the buffer
                                                                
                                                                streamingPlayer.closeConnection();      // Close the connection to the source media
                                                                log(0, "SSS.seek("+where+") [userSeek] - waiting for download thread to stop..");                                               
                                                                while(!downloadStopped && downloader.isAlive()) {       // Wait for download thread to terminate
                                                                        try{ Thread.sleep(100); } catch(Throwable t) {} 
                                                                }               
                                                                buffer.clear(); // Clear the buffer
                                                                log(0, "SSS.seek("+where+") [userSeek] - download thread stopped..");
                                                                                                                
                                                                // download from a little earlier than the seek position
                                                                boolean skipRequired = true;
                                                                bufferStartsAt = where-seekBuffer;              
                                                                if(bufferStartsAt<0){
                                                                        log(0, "SSS.seek("+where+") [userSeek] - Skipping not required.. bufferStartsAt: " + bufferStartsAt);
                                                                        bufferStartsAt = 0;
                                                                        skipRequired = false;
                                                                        log(0, "SSS.seek("+where+") [userSeek] - bufferStartsAt set to 0");
                                                                }
                                                                
                                                                bufferEndsAt = bufferStartsAt;  // set bufferEndsAt             
                                                                totalDownload = 0;
                                                                resetFlags();
                                                                initConnection(); //reopen connection with range header = buferStartsAt
                                                                
                                                                
                                                                bufferIStream.mark(buffer.getSize()-2);                                                         
                                                                dataSource.start();     // start a new Download thread
                                                                userSeek = false;
                                                                log(0, "SSS.seek("+where+") [userSeek] - waiting for initialBuffering to complete..");
                                                                while(!initialBufferingComplete){ // wait till initialBuffer is filled up.
                                                                        try { Thread.sleep(500); } catch (Throwable t) { }
                                                                }                                               
                                                                if(skipRequired){
                                                                        log(0, "SSS.seek("+where+") [userSeek] - Skipping seekBuffer: " + seekBuffer);
                                                                        bufferIStream.skip(seekBuffer);
                                                                }
                                                                skipRequired = true;
                                                                
                                                                log(0, "SSS.seek("+where+") [userSeek] - initialBufferingComplete");                                                                                    
                                                                
                                                                log(0, "SSS.seek("+where+") [userSeek] - " + now);                                                                                                                              
                                                        } catch (Throwable t){
                                                                userSeek = false;                                                               
                                                                notifyStreamingError(StreamingPlayerListener.ERROR_SEEKING);
                                                        }
                                                        return now;      
                                                } else{ // Cannot skip to a point outside of what the buffer holds now
                                                        log(0, "SSS.seek("+where+") - " + now + " [DUMMY]" +" bufferStartsAt: " + bufferStartsAt + " bufferEndsAt: " + bufferEndsAt);
                                                        return now;
                                                }
                                        } else if(getConnectionType()==CONNECTION_FILE){        //TODO: can be optimized a lot  
                                                synchronized(mediaIStream){     // block download until we are done seeking                     
                                                        try{
                                                                buffer.clear(); // clear the buffer
                                                                mediaIStream.reset();   // reset the file stream
                                                                long actualSkip = mediaIStream.skip(where);     // skip to where
                                                                bufferStartsAt = actualSkip;    // update bufferStartsAt
                                                                now = bufferStartsAt;   // update now
                                                                notifyNowReading(now);
                                                                bufferEndsAt = bufferStartsAt;  //update bufferEndsAt                                                                                                   
                                                        } catch(Throwable t){
                                                                notifyStreamingError(StreamingPlayerListener.ERROR_SEEKING);
                                                        }
                                                        return now;
                                                }
                                        } else{
                                                return now; 
                                        }
                                }
                        }
                }       

                /**
                 * Gets the current position on the stream. This is always 0 because the buffer loses data as they are sent
                 * to the underlying player.
                 */
                public long tell() {    
                        synchronized(readLock){
                                log(0, "SSS.tell() - " + now);                  
                                return now;
                        }
                }

                public Control getControl(String controlType) {                 
                        return null;
                }

                public Control[] getControls() {                        
                        return null;
                }
                
                public void close(){
                        
                }
                
        }

        /************************* End of SourceStream Implementation *************************/
        
        
        /************************* Downloader Thread *************************/
        
        private class Downloader extends Thread{                
                public void run(){
                        downloadStopped = false;
                        log(0, "DownloadThread - started");
                        try {                           
                                byte[] data = new byte[DOWNLOAD_CHUNK];
                                int len = 0;                    
                                
                                notifyFeedPaused(0);
                                while (-1 != (len = mediaIStream.read(data))) {
                                        log(1, "DownloadThread - read " + len);
                                        if (stopDownload){
                                                downloadStopped = true;                                                 
                                                log(0, "DownloadThread - stopped");
                                                return;                                         
                                        }
                                        synchronized(mediaIStream){                                             
                                                                                        
                                                byte[] preProcessed = notifyPreprocessData(data, 0, len);
                                                
                                                if(preProcessed!=null){
                                                        log(1, "Writing preProcessed bytes: " + preProcessed.length + "..");
                                                        bufferOStream.write(preProcessed, 0, preProcessed.length);                                      
                                                        bufferEndsAt += preProcessed.length;
                                                        log(1, "DownloadThread - preProcessed bytes written: " + preProcessed.length);                                          
                                                } else{
                                                        log(1, "Writing bytes: " + len + "..");
                                                        bufferOStream.write(data, 0, len);                                      
                                                        bufferEndsAt += len;
                                                        log(1, "DownloadThread - bytes written: " + len);
                                                }
                                                
                                                notifyBufferStatusChanged(bufferStartsAt, buffer.getSize() - buffer.getSpaceLeft());                                    
                                                
                                                
                                                totalDownload += len;
                                                notifyDownloadStatusUpdated(totalDownload);                                             
                                                
                                                if (!initialBufferingComplete && totalDownload >= initialBuffer) {                                                      
                                                        initialBufferingComplete = true;                                                
                                                        log(0, "DownloadThread - initialBuffering complete");
                                                        notifyInitialBufferCompleted(totalDownload);
                                                }                                       
                                        }
                                }                       
                                if(!initialBufferingComplete){
                                        initialBufferingComplete = true;
                                        notifyInitialBufferCompleted(totalDownload);
                                }
                                downloadComplete = true;
                                downloadStopped = true;
                                log(0, "DownloadThread - download stopped.");
                                log(0, "DownloadThread - download complete.");
                                
                        } catch (Throwable e) {
                                downloadStopped = true;
                                log(0, "DownloadThread - download stopped due to an Exception - "+e.toString());                                
                                
                                if(!reconnectInProgress && !userClose && !userSeek){
                                        synchronized(connectionLock){
                                                notifyStreamingError(StreamingPlayerListener.ERROR_DOWNLOADING);
                                                dataDiscarded = false;
                                                log(0, "DownloadThread - Trying to resume connection..");
                                                streamingPlayer.resumeDownload();       
                                                log(0, "DownloadThread - connected.. download resumed.");
                                        }
                                }
                        }
                }
        }
        
        /************************* End of Downloader Thread *************************/
        
        private class MediaTimeThread extends Thread{
                public void run(){                      
                        long tempTotal = 0;
                        long discardTime = 0;
                        log(0, "Started MediaTimeThread");
                        while(!stopTimer){
                                
                                synchronized(connectionLock){
                                        // Detect stale connection.
                                        if(dataDiscarded){
                                                
                                                if(tempTotal==0 && discardTime==0){
                                                        tempTotal = totalDownload;
                                                        discardTime = System.currentTimeMillis();
                                                        log(0, "MediaTimeThread - tempTotal, discardTime set");
                                                }
                                                
                                                if(dataDiscarded)                                               
                                                        log(0, "MediaTimeThread - " + ((totalDownload-tempTotal) < (availableAtDiscard+DOWNLOAD_CHUNK) && (System.currentTimeMillis()-discardTime) > connectionTimeout)+" "+(totalDownload - tempTotal) + "<>" + (availableAtDiscard+DOWNLOAD_CHUNK) + " : " + (System.currentTimeMillis()-discardTime));
                                                
                                                if((totalDownload-tempTotal) < (availableAtDiscard+DOWNLOAD_CHUNK) && (System.currentTimeMillis()-discardTime) > connectionTimeout){
                                                        dataDiscarded = false;
                                                        tempTotal = 0;
                                                        discardTime = 0;
                                                        availableAtDiscard = 0;
                                                        log(0, "MediaTimeThread - Trying to resume connection..");
                                                        reconnectInProgress = true;
                                                        streamingPlayer.resumeDownload();                                                       
                                                        log(0, "MediaTimeThread - download resumed.");                                  
                                                } else if((totalDownload - tempTotal) >= (availableAtDiscard+DOWNLOAD_CHUNK) && (System.currentTimeMillis()-discardTime) > connectionTimeout){
                                                        dataDiscarded = false;
                                                        tempTotal = 0;
                                                        discardTime = 0;
                                                        availableAtDiscard = 0;
                                                        log(0, "MediaTimeThread - connection was not stale.");
                                                }
                                        } else{
                                                dataDiscarded = false;
                                                tempTotal = 0;
                                                discardTime = 0;
                                                availableAtDiscard = 0;
                                        }
                                }
                                
                                if(state!=UNREALIZED){                                  
                                        try{
                                                notifyNowPlaying(player.getMediaTime());
                                        } catch(IllegalStateException ise){
                                                log(0, "FAILED! P.getMediaTime(): " + ise);
                                        }
                                }
                                try{ Thread.sleep(100); } catch(Throwable t){ }
                        }
                        log(0, "Stopped MediaTimeThread");
                }
        }
}

