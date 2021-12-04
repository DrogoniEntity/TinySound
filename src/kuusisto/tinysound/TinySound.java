/*
 * Copyright (c) 2012, Finn Kuusisto
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *     
 *     Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package kuusisto.tinysound;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import kuusisto.tinysound.event.SoundEventListener;
import kuusisto.tinysound.internal.ByteList;
import kuusisto.tinysound.internal.EventHandler;
import kuusisto.tinysound.internal.MemMusic;
import kuusisto.tinysound.internal.MemSound;
import kuusisto.tinysound.internal.Mixer;
import kuusisto.tinysound.internal.StreamInfo;
import kuusisto.tinysound.internal.StreamMusic;
import kuusisto.tinysound.internal.StreamSound;
import kuusisto.tinysound.internal.UpdateRunner;

/**
 * TinySound is the main class of the TinySound system. In order to use the
 * TinySound system, it must be initialized. After that, Music and Sound objects
 * can be loaded and used. When finished with the TinySound system, it must be
 * shutdown.
 * 
 * @author Finn Kuusisto
 */
public class TinySound {

    public static final String VERSION = "fork";

    /**
     * The internal format used by TinySound.
     */
    public static final AudioFormat FORMAT = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, // linear signed PCM
	    44100, // 44.1kHz sampling rate
	    16, // 16-bit
	    2, // 2 channels fool
	    4, // frame size 4 bytes (16-bit, 2 channel)
	    44100, // same as sampling rate
	    false // little-endian
    );

    // the system has only one mixer for both music and sounds
    private Mixer mixer;
    // need a line to the speakers
    private SourceDataLine outLine;
    // see if the system has been initialized
    private static boolean inited = false;
    // auto-updater for the system
    private UpdateRunner autoUpdater;
    // counter for unique sound IDs
    private int soundCount = 0;
    // TinySoundListener manager
    private EventHandler listenersManager;

    // prevent to use any constructors
    private TinySound(SourceDataLine outLine) {
	this.outLine = outLine;
    }

    /**
     * Initialize Tinysound. This must be called before loading audio.
     */
    public static TinySound init() throws IllegalStateException, UnsupportedOperationException, NullPointerException {
	if (TinySound.inited) {
	    throw new IllegalStateException("TinySound already initialized");
	}
	// try to open a line to the speakers
	DataLine.Info info = new DataLine.Info(SourceDataLine.class, TinySound.FORMAT);
	if (!AudioSystem.isLineSupported(info)) {
	    throw new UnsupportedOperationException("Unsupported output format");
	}
	SourceDataLine outLine = TinySound.tryGetLine();
	if (outLine == null) {
	    throw new NullPointerException("Output line unavailable!");
	}

	// start the line and finish initialization
	TinySound instance = new TinySound(outLine);
	outLine.start();
	instance.finishInit();

	return instance;
    }

    /**
     * Alternative function to initialize TinySound which should only be used by
     * those very familiar with the Java Sound API. This function allows the line
     * that is used for audio playback to be opened on a specific Mixer.
     * 
     * @param info the Mixer.Info representing the desired Mixer
     * @throws LineUnavailableException if a Line is not available from the
     *                                  specified Mixer
     * @throws SecurityException        if the specified Mixer or Line are
     *                                  unavailable due to security restrictions
     * @throws IllegalArgumentException if the specified Mixer is not installed on
     *                                  the system
     */
    public static TinySound init(javax.sound.sampled.Mixer.Info info)
	    throws IllegalStateException, LineUnavailableException, SecurityException, IllegalArgumentException {
	if (TinySound.inited) {
	    throw new IllegalStateException("TinySound already initialized");
	}
	// try to open a line to the speakers
	javax.sound.sampled.Mixer mixer = AudioSystem.getMixer(info);
	DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, TinySound.FORMAT);
	SourceDataLine outLine = (SourceDataLine) mixer.getLine(lineInfo);
	outLine.open(TinySound.FORMAT);

	// start the line and finish initialization
	TinySound instance = new TinySound(outLine);
	outLine.start();
	instance.finishInit();

	return instance;
    }

    /**
     * Initializes the mixer and updater, and marks TinySound as initialized.
     */
    private void finishInit() {
	// initialize listener manager
	this.listenersManager = new EventHandler();
	// now initialize the mixer
	this.mixer = new Mixer(this.listenersManager);
	// initialize and start the updater
	this.autoUpdater = new UpdateRunner(this.mixer, this.outLine);
	Thread updateThread = new Thread(this.autoUpdater);
	try {
	    updateThread.setDaemon(true);
	    updateThread.setPriority(Thread.MAX_PRIORITY);
	} catch (Exception e) {
	}
	TinySound.inited = true;
	updateThread.start();
	// yield to potentially give the updater a chance
	Thread.yield();
    }

    /**
     * Shutdown TinySound.
     */
    public void shutdown() throws IllegalStateException {
	if (!TinySound.inited) {
	    throw new IllegalStateException("TinySound not initialized");
	}
	// stop the auto-updater if running
	this.autoUpdater.stop();

	// clear resources
	this.autoUpdater = null;
	this.outLine.stop();
	this.outLine.flush();
	this.mixer.clearMusic();
	this.mixer.clearSounds();
	this.mixer = null;
	this.listenersManager = null;

	// and clear inited flag
	TinySound.inited = false;
    }

    /**
     * Determine if TinySound is initialized and ready for use.
     * 
     * @return true if TinySound is initialized, false if TinySound has not been
     *         initialized or has subsequently been shutdown
     */
    public static boolean isInitialized() {
	return TinySound.inited;
    }

    /**
     * Get the global volume for all audio.
     * 
     * @return the global volume for all audio, -1.0 if TinySound has not been
     *         initialized or has subsequently been shutdown
     */
    public double getGlobalVolume() {
	return this.mixer.getVolume();
    }

    /**
     * Set the global volume. This is an extra multiplier, not a replacement, for
     * all Music and Sound volume settings. It starts at 1.0.
     * 
     * @param volume the global volume to set
     */
    public void setGlobalVolume(double volume) {
	this.mixer.setVolume(volume);
    }

    /**
     * Load a Music by a resource name. The resource must be on the classpath for
     * this to work. This will store audio data in memory.
     * 
     * @param name name of the Music resource
     * @return Music resource as specified, null if not found/loaded
     * @throws NullPointerException if name is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if resource couldn't be found or something went wrong during music loading
     */
    public Music loadMusic(String name) throws NullPointerException, UnsupportedAudioFileException, IOException {
	return this.loadMusic(name, false);
    }

    /**
     * Load a Music by a resource name. The resource must be on the classpath for
     * this to work.
     * 
     * @param name           name of the Music resource
     * @param streamFromFile true if this Music should be streamed from a temporary
     *                       file to reduce memory overhead
     * @return Music resource as specified
     * @throws NullPointerException if name is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if resource couldn't be found or something went wrong during music loading
     */
    public Music loadMusic(String name, boolean streamFromFile) throws NullPointerException, UnsupportedAudioFileException, IOException {
	// check for failure
	if (name == null) {
	    throw new NullPointerException("name is null");
	}
	// check for correct naming
	if (!name.startsWith("/")) {
	    name = "/" + name;
	}
	URL url = TinySound.class.getResource(name);
	// check for failure to find resource
	if (url == null) {
	    throw new FileNotFoundException("Unable to find resource " + name + "!");
	}
	return this.loadMusic(url, streamFromFile);
    }

    /**
     * Load a Music by a File. This will store audio data in memory.
     * 
     * @param file the Music file to load
     * @return Music from file as specified
     * @throws NullPointerException if file is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if file isn't found or something went wrong during music loading
     */
    public Music loadMusic(File file) throws NullPointerException, UnsupportedAudioFileException, IOException {
	return this.loadMusic(file, false);
    }

    /**
     * Load a Music by a File.
     * 
     * @param file           the Music file to load
     * @param streamFromFile true if this Music should be streamed from a temporary
     *                       file to reduce memory overhead
     * @return Music from file as specified
     * @throws NullPointerException if file is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if file isn't found or something went wrong during music loading
     */
    public Music loadMusic(File file, boolean streamFromFile) throws NullPointerException, UnsupportedAudioFileException, IOException {
	// check for failure
	if (file == null) {
	    throw new NullPointerException("file is null");
	}
	URL url = null;
	try {
	    url = file.toURI().toURL();
	} catch (MalformedURLException e) {
	    throw new FileNotFoundException("unable to find file " + file);
	}
	return this.loadMusic(url, streamFromFile);
    }

    /**
     * Load a Music by a URL. This will store audio data in memory.
     * 
     * @param url the URL of the Music
     * @return Music from URL as specified
     * @throws NullPointerException if url is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during music loading
     */
    public Music loadMusic(URL url) throws NullPointerException, UnsupportedAudioFileException, IOException {
	return this.loadMusic(url, false);
    }

    /**
     * Load a Music by a URL.
     * 
     * @param url            the URL of the Music
     * @param streamFromFile true if this Music should be streamed from a temporary
     *                       file to reduce memory overhead
     * @return Music from URL as specified
     * @throws NullPointerException if url is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during music loading
     */
    public Music loadMusic(URL url, boolean streamFromFile) throws NullPointerException, UnsupportedAudioFileException, IOException {
	// check for failure
	if (url == null) {
	    throw new NullPointerException("url is null");
	}
	// get a valid stream of audio data
	AudioInputStream audioStream = TinySound.getValidAudioStream(url);
	
	// try to read all the bytes
	byte[][] data = TinySound.readAllBytes(audioStream);
	
	// handle differently if streaming from a file
	if (streamFromFile) {
	    StreamInfo info = TinySound.createFileStream(data);
	    return new StreamMusic(info.URL, info.NUM_BYTES_PER_CHANNEL, this.mixer);
	}
	// construct the Music object and register it with the mixer
	return new MemMusic(data[0], data[1], this.mixer);
    }
    
    /**
     * Load a Music by a InpuStream. This will store data from memory.
     * 
     * @param stream         Music's resource stream
     * @return Music from InpuStream as specified
     * @throws NullPointerException if stream is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during music loading
     */
    public Music loadMusic(InputStream stream) throws NullPointerException, UnsupportedAudioFileException, IOException
    {
	return this.loadMusic(stream, false);
    }
    
    /**
     * Load a Music by a InpuStream.
     * 
     * @param stream         Music's resource stream
     * @param streamFromFile true if this Music should be streamed from a temporary
     *                       file to reduce memory overhead
     * @return Music from URL as specified
     * @throws NullPointerException if url is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during music loading
     */
    public Music loadMusic(InputStream stream, boolean streamFromFile) throws NullPointerException, UnsupportedAudioFileException, IOException  {
	if (stream == null)
	    throw new NullPointerException("stream is null");
	
	// getting audio stream
	AudioInputStream audioStream = AudioSystem.getAudioInputStream(stream);
	return this.loadMusic(audioStream, streamFromFile);	
    }
    
    /**
     * Load a Music by a AudioInpuStream. This will store data from memory.
     * 
     * @param stream         Music's resource stream
     * @return Music from URL as specified
     * @throws NullPointerException if url is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during music loading
     */
    public Music loadMusic(AudioInputStream audioStream) throws NullPointerException, UnsupportedAudioFileException, IOException {
	return this.loadMusic(audioStream, false);
    }
    
    /**
     * Load a Music by a AudioInpuStream.
     * 
     * @param stream         Music's resource stream
     * @param streamFromFile true if this Music should be streamed from a temporary
     *                       file to reduce memory overhead
     * @return Music from URL as specified
     * @throws NullPointerException if url is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during music loading
     */
    public Music loadMusic(AudioInputStream audioStream, boolean streamFromFile) throws NullPointerException, UnsupportedAudioFileException, IOException {
	if (audioStream == null)
	    throw new NullPointerException("stream is null");
	
	// convert it
	audioStream = TinySound.convertAudioStream(audioStream);
	
	// try to read all the bytes
	byte[][] data = TinySound.readAllBytes(audioStream);
	
	// handle differently if streaming from a file
	if (streamFromFile) {
	    StreamInfo info = TinySound.createFileStream(data);
	    return new StreamMusic(info.URL, info.NUM_BYTES_PER_CHANNEL, this.mixer);
	}
	
	// construct the Music object and register it with the mixer
	return new MemMusic(data[0], data[1], this.mixer);
    }

    /**
     * Load a Sound by a resource name. The resource must be on the classpath for
     * this to work. This will store audio data in memory.
     * 
     * @param name name of the Sound resource
     * @return Sound resource as specified
     * @throws NullPointerException if name is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if resource couldn't be found or something went wrong during sound loading
     */
    public Sound loadSound(String name) throws NullPointerException, UnsupportedAudioFileException, IOException  {
	return this.loadSound(name, false);
    }

    /**
     * Load a Sound by a resource name. The resource must be on the classpath for
     * this to work.
     * 
     * @param name           name of the Sound resource
     * @param streamFromFile true if this Music should be streamed from a temporary
     *                       file to reduce memory overhead
     * @return Sound resource as specified
     * @throws NullPointerException if name is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if resource couldn't be found or something went wrong during sound loading
     */
    public Sound loadSound(String name, boolean streamFromFile) throws NullPointerException, UnsupportedAudioFileException, IOException {
	// check for failure
	if (name == null) {
	    throw new NullPointerException("name is null");
	}
	// check for correct naming
	if (!name.startsWith("/")) {
	    name = "/" + name;
	}
	URL url = TinySound.class.getResource(name);
	// check for failure to find resource
	if (url == null) {
	    throw new FileNotFoundException("Unable to find resource " + name + "!");
	}
	return this.loadSound(url, streamFromFile);

    }

    /**
     * Load a Sound by a File. This will store audio data in memory.
     * 
     * @param file the Sound file to load
     * @return Sound from file as specified
     * @throws NullPointerException if file is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during sound loading
     */
    public Sound loadSound(File file) throws NullPointerException, UnsupportedAudioFileException, IOException {
	return this.loadSound(file, false);
    }

    /**
     * Load a Sound by a File.
     * 
     * @param file           the Sound file to load
     * @param streamFromFile true if this Music should be streamed from a temporary
     *                       file to reduce memory overhead
     * @return Sound from file as specified
     * @throws NullPointerException if file is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during sound loading
     */
    public Sound loadSound(File file, boolean streamFromFile) throws NullPointerException, UnsupportedAudioFileException, IOException {
	// check for failure
	if (file == null) {
	    throw new NullPointerException("file is null");
	}
	URL url = null;
	try {
	    url = file.toURI().toURL();
	} catch (MalformedURLException e) {
	    throw new FileNotFoundException("Unable to find file " + file + "!");
	}
	return this.loadSound(url, streamFromFile);
    }

    /**
     * Load a Sound by a URL. This will store audio data in memory.
     * 
     * @param url the URL of the Sound
     * @return Sound from URL as specified
     * @throws NullPointerException if url is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during sound loading
     */
    public Sound loadSound(URL url) throws NullPointerException, UnsupportedAudioFileException, IOException {
	return this.loadSound(url, false);
    }

    /**
     * Load a Sound by a URL. This will store audio data in memory.
     * 
     * @param url            the URL of the Sound
     * @param streamFromFile true if this Music should be streamed from a temporary
     *                       file to reduce memory overhead
     * @return Sound from URL as specified
     * @throws NullPointerException if url is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during sound loading
     */
    public Sound loadSound(URL url, boolean streamFromFile) throws NullPointerException, UnsupportedAudioFileException, IOException {
	// check for failure
	if (url == null) {
	    throw new NullPointerException("url is null");
	}
	// get a valid stream of audio data
	AudioInputStream audioStream = TinySound.getValidAudioStream(url);
	
	// try to read all the bytes
	byte[][] data = TinySound.readAllBytes(audioStream);
	
	// handle differently if streaming from file
	if (streamFromFile) {
	    StreamInfo info = TinySound.createFileStream(data);
	    
	    // try to create it
	    this.soundCount++;
	    return new StreamSound(info.URL, info.NUM_BYTES_PER_CHANNEL, this.mixer, this.soundCount);
	}
	// construct the Sound object
	this.soundCount++;
	return new MemSound(data[0], data[1], this.mixer, this.soundCount);
    }
    
    /**
     * Load a Sound by a InpuStream. This will store data from memory.
     * 
     * @param stream         Sound's resource stream
     * @return Music from InpuStream as specified
     * @throws NullPointerException if stream is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during music loading
     */
    public Sound loadSound(InputStream stream) throws NullPointerException, UnsupportedAudioFileException, IOException
    {
	return this.loadSound(stream, false);
    }
    
    /**
     * Load a Sound by a InpuStream.
     * 
     * @param stream         Music's resource stream
     * @param streamFromFile true if this Music should be streamed from a temporary
     *                       file to reduce memory overhead
     * @return Sound from InputStream as specified
     * @throws NullPointerException if stream is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during music loading
     */
    public Sound loadSound(InputStream stream, boolean streamFromFile) throws NullPointerException, UnsupportedAudioFileException, IOException  {
	if (stream == null)
	    throw new NullPointerException("stream is null");
	
	// getting audio stream
	AudioInputStream audioStream = AudioSystem.getAudioInputStream(stream);
	return this.loadSound(audioStream, streamFromFile);	
    }
    
    /**
     * Load a Sound by a AudioInpuStream. This will store data from memory.
     * 
     * @param stream         Sound's resource stream
     * @return Sound from InputStream as specified
     * @throws NullPointerException if audioStream is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during music loading
     */
    public Sound loadSound(AudioInputStream audioStream) throws NullPointerException, UnsupportedAudioFileException, IOException {
	return this.loadSound(audioStream, false);
    }
    
    /**
     * Load a Music by a AudioInpuStream.
     * 
     * @param stream         Sound's resource stream
     * @param streamFromFile true if this Sound should be streamed from a temporary
     *                       file to reduce memory overhead
     * @return Sound from InputStream as specified
     * @throws NullPointerException if url is null
     * @throws UnsupportedAudioFileException if requested audio couldn't be used
     * @throws IOException if something went wrong during music loading
     */
    public Sound loadSound(AudioInputStream audioStream, boolean streamFromFile) throws NullPointerException, UnsupportedAudioFileException, IOException {
	if (audioStream == null)
	    throw new NullPointerException("stream is null");
	
	// convert it
	audioStream = TinySound.convertAudioStream(audioStream);
	
	// try to read all the bytes
	byte[][] data = TinySound.readAllBytes(audioStream);
	
	// handle differently if streaming from a file
	if (streamFromFile) {
	    StreamInfo info = TinySound.createFileStream(data);
	    this.soundCount++;
	    return new StreamSound(info.URL, info.NUM_BYTES_PER_CHANNEL, this.mixer, this.soundCount);
	}
	
	// construct the Music object and register it with the mixer
	return new MemSound(data[0], data[1], this.mixer, this.soundCount);
    }

    /**
     * Reads all of the bytes from an AudioInputStream.
     * 
     * @param stream the stream to read
     * @return all bytes from the stream, null if error
     */
    private static byte[][] readAllBytes(AudioInputStream stream) throws IOException {
	// left and right channels
	byte[][] data = null;
	int numChannels = stream.getFormat().getChannels();
	// handle 1-channel
	if (numChannels == 1) {
	    byte[] left = TinySound.readAllBytesOneChannel(stream);
	    
	    data = new byte[2][];
	    data[0] = left;
	    data[1] = left; // don't copy for the right channel
	} // handle 2-channel
	else if (numChannels == 2) {
	    data = TinySound.readAllBytesTwoChannel(stream);
	} else { // wtf?
	    throw new IOException("Unable to read " + numChannels + " channels!");
	}
	return data;
    }

    /**
     * Reads all of the bytes from a 1-channel AudioInputStream.
     * 
     * @param stream the stream to read
     * @return all bytes from the stream, null if error
     */
    private static byte[] readAllBytesOneChannel(AudioInputStream stream) throws IOException {
	// read all the bytes (assuming 1-channel)
	byte[] data = null;
	try {
	    data = TinySound.getBytes(stream);
	} catch (IOException e) {
	    throw new IOException("Error reading all bytes from stream!", e);
	} finally {
	    try {
		stream.close();
	    } catch (IOException e) {
	    }
	}
	return data;
    }

    /**
     * Reads all of the bytes from a 2-channel AudioInputStream.
     * 
     * @param stream the stream to read
     * @return all bytes from the stream, null if error
     */
    private static byte[][] readAllBytesTwoChannel(AudioInputStream stream) throws IOException {
	// read all the bytes (assuming 16-bit, 2-channel)
	byte[][] data = null;
	try {
	    byte[] allBytes = TinySound.getBytes(stream);
	    byte[] left = new byte[allBytes.length / 2];
	    byte[] right = new byte[allBytes.length / 2];
	    for (int i = 0, j = 0; i < allBytes.length; i += 4, j += 2) {
		// interleaved left then right
		left[j] = allBytes[i];
		left[j + 1] = allBytes[i + 1];
		right[j] = allBytes[i + 2];
		right[j + 1] = allBytes[i + 3];
	    }
	    data = new byte[2][];
	    data[0] = left;
	    data[1] = right;
	} catch (IOException e) {
	    throw new IOException("Error reading all bytes from stream!", e);
	} finally {
	    try {
		stream.close();
	    } catch (IOException e) {
	    }
	}
	return data;
    }

    /**
     * Gets and AudioInputStream in the TinySound system format.
     * 
     * @param url URL of the resource
     * @return the specified stream as an AudioInputStream stream, null if failure
     * @throws UnsupportedAudioFileException 
     */
    private static AudioInputStream getValidAudioStream(URL url) throws IOException, UnsupportedAudioFileException {
	AudioInputStream audioStream = AudioSystem.getAudioInputStream(url);
	return convertAudioStream(audioStream);
    }

    @SuppressWarnings("resource")
    private static AudioInputStream convertAudioStream(AudioInputStream audioStream) throws IOException, UnsupportedAudioFileException {
	AudioFormat streamFormat = audioStream.getFormat();
	// 1-channel can also be treated as stereo
	AudioFormat mono16 = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false);
	// 1 or 2 channel 8-bit may be easy to convert
	AudioFormat mono8 = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 8, 1, 1, 44100, false);
	AudioFormat stereo8 = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 8, 2, 2, 44100, false);
	// now check formats (attempt conversion as needed)
	if (streamFormat.matches(TinySound.FORMAT) || streamFormat.matches(mono16)) {
	    return audioStream;
	} // check conversion to TinySound format
	else if (AudioSystem.isConversionSupported(TinySound.FORMAT, streamFormat)) {
	    audioStream = AudioSystem.getAudioInputStream(TinySound.FORMAT, audioStream);
	} // check conversion to mono alternate
	else if (AudioSystem.isConversionSupported(mono16, streamFormat)) {
	    audioStream = AudioSystem.getAudioInputStream(mono16, audioStream);
	} // try convert from 8-bit, 2-channel
	else if (streamFormat.matches(stereo8) || AudioSystem.isConversionSupported(stereo8, streamFormat)) {
	    // convert to 8-bit stereo first?
	    if (!streamFormat.matches(stereo8)) {
		audioStream = AudioSystem.getAudioInputStream(stereo8, audioStream);
	    }
	    audioStream = TinySound.convertStereo8Bit(audioStream);
	} // try convert from 8-bit, 1-channel
	else if (streamFormat.matches(mono8) || AudioSystem.isConversionSupported(mono8, streamFormat)) {
	    // convert to 8-bit mono first?
	    if (!streamFormat.matches(mono8)) {
		audioStream = AudioSystem.getAudioInputStream(mono8, audioStream);
	    }
	    audioStream = TinySound.convertMono8Bit(audioStream);
	} // it's time to give up
	else {
	    throw new IOException("couldn't convert audio stream !");
	}
	// check the frame length
	long frameLength = audioStream.getFrameLength();
	// too long
	if (frameLength > Integer.MAX_VALUE) {
	    throw new UnsupportedAudioFileException("Audio resource too long!");
	}

	return audioStream;
    }

    /**
     * Converts an 8-bit, signed, 1-channel AudioInputStream to 16-bit, signed,
     * 1-channel.
     * 
     * @param stream stream to convert
     * @return converted stream
     */
    private static AudioInputStream convertMono8Bit(AudioInputStream stream) throws IOException, UnsupportedAudioFileException {
	// assuming 8-bit, 1-channel to 16-bit, 1-channel
	byte[] newData = null;
	try {
	    byte[] data = TinySound.getBytes(stream);
	    int newNumBytes = data.length * 2;
	    // check if size overflowed
	    if (newNumBytes < 0) {
		throw new UnsupportedAudioFileException("Audio resource too long!");
	    }
	    newData = new byte[newNumBytes];
	    // convert bytes one-by-one to int, and then to 16-bit
	    for (int i = 0, j = 0; i < data.length; i++, j += 2) {
		// convert it to a double
		double floatVal = (double) data[i];
		floatVal /= (floatVal < 0) ? 128 : 127;
		if (floatVal < -1.0) { // just in case
		    floatVal = -1.0;
		} else if (floatVal > 1.0) {
		    floatVal = 1.0;
		}
		// convert it to an int and then to 2 bytes
		int val = (int) (floatVal * Short.MAX_VALUE);
		newData[j + 1] = (byte) ((val >> 8) & 0xFF); // MSB
		newData[j] = (byte) (val & 0xFF); // LSB
	    }
	} catch (IOException e) {
	    throw new IOException("Error reading all bytes from stream!", e);
	} finally {
	    try {
		stream.close();
	    } catch (IOException e) {
	    }
	}
	AudioFormat mono16 = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false);
	return new AudioInputStream(new ByteArrayInputStream(newData), mono16, newData.length / 2);
    }

    /**
     * Converts an 8-bit, signed, 2-channel AudioInputStream to 16-bit, signed,
     * 2-channel.
     * 
     * @param stream stream to convert
     * @return converted stream
     */
    private static AudioInputStream convertStereo8Bit(AudioInputStream stream) throws IOException, UnsupportedAudioFileException {
	// assuming 8-bit, 2-channel to 16-bit, 2-channel
	byte[] newData = null;
	try {
	    byte[] data = TinySound.getBytes(stream);
	    int newNumBytes = data.length * 2 * 2;
	    // check if size overflowed
	    if (newNumBytes < 0) {
		throw new UnsupportedAudioFileException("Audio resource too long!");
	    }
	    newData = new byte[newNumBytes];
	    for (int i = 0, j = 0; i < data.length; i += 2, j += 4) {
		// convert them to doubles
		double leftFloatVal = (double) data[i];
		double rightFloatVal = (double) data[i + 1];
		leftFloatVal /= (leftFloatVal < 0) ? 128 : 127;
		rightFloatVal /= (rightFloatVal < 0) ? 128 : 127;
		if (leftFloatVal < -1.0) { // just in case
		    leftFloatVal = -1.0;
		} else if (leftFloatVal > 1.0) {
		    leftFloatVal = 1.0;
		}
		if (rightFloatVal < -1.0) { // just in case
		    rightFloatVal = -1.0;
		} else if (rightFloatVal > 1.0) {
		    rightFloatVal = 1.0;
		}
		// convert them to ints and then to 2 bytes each
		int leftVal = (int) (leftFloatVal * Short.MAX_VALUE);
		int rightVal = (int) (rightFloatVal * Short.MAX_VALUE);
		// left channel bytes
		newData[j + 1] = (byte) ((leftVal >> 8) & 0xFF); // MSB
		newData[j] = (byte) (leftVal & 0xFF); // LSB
		// then right channel bytes
		newData[j + 3] = (byte) ((rightVal >> 8) & 0xFF); // MSB
		newData[j + 2] = (byte) (rightVal & 0xFF); // LSB
	    }
	} catch (IOException e) {
	    throw new IOException("Error reading all bytes from stream!", e);
	} finally {
	    try {
		stream.close();
	    } catch (IOException e) {
	    }
	}
	AudioFormat stereo16 = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
	return new AudioInputStream(new ByteArrayInputStream(newData), stereo16, newData.length / 4);
    }

    /**
     * Read all of the bytes from an AudioInputStream.
     * 
     * @param stream the stream from which to read bytes
     * @return all bytes read from the AudioInputStream
     * @throws IOException
     */
    private static byte[] getBytes(AudioInputStream stream) throws IOException {
	// buffer 1-sec at a time
	int bufSize = (int) TinySound.FORMAT.getSampleRate() * TinySound.FORMAT.getChannels()
		* TinySound.FORMAT.getFrameSize();
	byte[] buf = new byte[bufSize];
	ByteList list = new ByteList(bufSize);
	int numRead = 0;
	while ((numRead = stream.read(buf)) > -1) {
	    for (int i = 0; i < numRead; i++) {
		list.add(buf[i]);
	    }
	}
	return list.asArray();
    }

    /**
     * Dumps audio data to a temporary file for streaming and returns a StreamInfo
     * for the stream.
     * 
     * @param data the audio data to write to the temporary file
     * @return a StreamInfo for the stream
     */
    private static StreamInfo createFileStream(byte[][] data) throws IOException {
	// first try to create a file for the data to live in
	File temp = null;
	try {
	    temp = File.createTempFile("tiny", "sound");
	    // make sure this file will be deleted on exit
	    temp.deleteOnExit();
	} catch (IOException e) {
	    throw new IOException("Failed to create file for streaming!", e);
	}
	// see if we can get the URL for this file
	URL url = null;
	try {
	    url = temp.toURI().toURL();
	} catch (MalformedURLException e1) {
	    throw new IOException("Failed to get URL for stream file!", e1);
	}
	// we have the file, now we want to be able to write to it
	OutputStream out = null;
	try {
	    out = new BufferedOutputStream(new FileOutputStream(temp), (512 * 1024)); // buffer 512kb
	} catch (FileNotFoundException e) {
	    throw new IOException("Failed to open stream file for writing!", e);
	}
	// write the bytes to the file
	try {
	    // write two at a time from each channel
	    for (int i = 0; i < data[0].length; i += 2) {
		try {
		    // first left
		    out.write(data[0], i, 2);
		    // then right
		    out.write(data[1], i, 2);
		} catch (IOException e) {
		    // hmm
		    throw new IOException("Failed writing bytes to stream file!", e);
		}
	    }
	} finally {
	    try {
		out.close();
	    } catch (IOException e) {
		// what?
		System.err.println("Failed closing stream file after writing!");
	    }
	}
	return new StreamInfo(url, data[0].length);
    }

    /**
     * Iterates through available JavaSound Mixers looking for one that can provide
     * a line to the speakers.
     * 
     * @return an opened SourceDataLine to the speakers
     */
    private static SourceDataLine tryGetLine() {
	// first build our line info and get all available mixers
	DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, TinySound.FORMAT);
	javax.sound.sampled.Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
	// iterate through the mixers trying to find a line
	for (int i = 0; i < mixerInfos.length; i++) {
	    javax.sound.sampled.Mixer mixer = null;
	    try {
		// first try to actually get the mixer
		mixer = AudioSystem.getMixer(mixerInfos[i]);
	    } catch (SecurityException e) {
		// not much we can do here
	    } catch (IllegalArgumentException e) {
		// this should never happen since we were told the mixer exists
	    }
	    // check if we got a mixer and our line is supported
	    if (mixer == null || !mixer.isLineSupported(lineInfo)) {
		continue;
	    }
	    // see if we can actually get a line
	    SourceDataLine line = null;
	    try {
		line = (SourceDataLine) mixer.getLine(lineInfo);
		// don't try to open if already open
		if (!line.isOpen()) {
		    line.open(TinySound.FORMAT);
		}
	    } catch (LineUnavailableException e) {
		// we either failed to get or open
		// should we do anything here?
	    } catch (SecurityException e) {
		// not much we can do here
	    }
	    // check if we succeeded
	    if (line != null && line.isOpen()) {
		return line;
	    }
	}
	// no good
	return null;
    }

    public void registerEventListener(SoundEventListener listener) throws NullPointerException {
	this.listenersManager.registerListener(listener);
    }

    public void unregisterEventListener(SoundEventListener listener) throws NullPointerException {
	this.listenersManager.unregisterListener(listener);
    }

}
