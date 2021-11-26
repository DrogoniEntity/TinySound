import java.io.File;
import java.util.Random;

import kuusisto.tinysound.Music;
import kuusisto.tinysound.Sound;
import kuusisto.tinysound.TinySound;
import kuusisto.tinysound.event.EventAction;
import kuusisto.tinysound.event.MusicEvent;
import kuusisto.tinysound.event.SoundEvent;
import kuusisto.tinysound.event.TinySoundEventListener;

public class EventListenerExample
{
    private static boolean running;
    
    public static void main(String args[]) throws Throwable
    {
        TinySound.init();
        TinySound.registerEventListener(new MyEventListener());
        running = true;
        
        Music music = TinySound.loadMusic(new File("groundtheme.wav"));
        Sound sound = TinySound.loadSound(new File("jump.wav"));
        
        Random random = new Random();
        music.play(false);
        while (running)
        {
            Thread.sleep(5000L);
            if (running && random.nextInt(10) > 3)
            {
                sound.play();
            }
        }
        
        System.out.println("Shutting down...");
        sound.stop();
        TinySound.shutdown();
    }
    
    public static class MyEventListener implements TinySoundEventListener
    {
        
        @Override
        public void onMusicEvent(MusicEvent event)
        {
            if (event.getAction() == EventAction.PLAY)
                System.out.println("My music begin to play !");
            else
            {
                System.out.println("My music has reached ! Now stopping...");
                EventListenerExample.running = false;
            }
        }

        @Override
        public void onSoundEvent(SoundEvent event)
        {
            if (event.getAction() == EventAction.PLAY)
                System.out.println("Playing sound " + event.getSound());
            else
                System.out.println("Sound " + event.getSound() + " ended !");
        }
        
    }
}
