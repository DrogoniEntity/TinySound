import java.io.File;
import java.util.Random;

import kuusisto.tinysound.Music;
import kuusisto.tinysound.Sound;
import kuusisto.tinysound.TinySound;
import kuusisto.tinysound.event.MusicEvent;
import kuusisto.tinysound.event.SoundEvent;
import kuusisto.tinysound.event.SoundEventListener;

public class EventListenerExample {
    private static boolean running;

    public static void main(String args[]) throws Throwable {
	//init library
	TinySound lib = TinySound.init();
	lib.registerEventListener(new MyEventListener());

	//loading resources
	Music music = lib.loadMusic(new File("groundtheme.wav"));
	Sound sound = lib.loadSound(new File("jump.wav"));
	
	//setting up loop
	music.setLoopPositionsByFrame(0, -1);
	music.play(false);			//change to true to enable looping
	
	running = true;
	Random random = new Random();
	while (running) {
	    Thread.sleep(5000L);
	    if (running && random.nextInt(10) > 3) {
		sound.play();
	    }
	}

	System.out.println("- Shutting down...");
	sound.stop();
	lib.shutdown();
    }

    public static class MyEventListener implements SoundEventListener {

	@Override
	public void onMusicEvent(MusicEvent event) {
	    switch (event.getAction()) {
	    case PLAY:
		System.out.println("> My music begin to play !");
		break;
	    case LOOP:
		System.out.println("> My music loop !");
		break;
	    case STOP:
		System.out.println("> My music has been stopped !");
		EventListenerExample.running = false;
		break;
	    }
	}

	@Override
	public void onSoundEvent(SoundEvent event) {
	    switch (event.getAction())
	    {
	    case PLAY:
		System.out.println("Playing sound...");
		break;
	    case STOP:
		System.out.println("Sound playback done !");
		break;
	    }
	}

    }
}
