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
package kuusisto.tinysound.internal;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import kuusisto.tinysound.event.MusicEvent;
import kuusisto.tinysound.event.SoundEvent;
import kuusisto.tinysound.event.TinySoundEventListener;

public class EventHandler
{
    
    private final Set<TinySoundEventListener> listeners;
    
    public EventHandler()
    {
        this.listeners = ConcurrentHashMap.newKeySet();
    }
    
    public void registerListener(TinySoundEventListener listener) throws NullPointerException
    {
        if (listener == null)
            throw new NullPointerException("listener is null");
        this.listeners.add(listener);
    }
    
    public void unregisterListener(TinySoundEventListener listener) throws NullPointerException
    {
        if (listener == null)
            throw new NullPointerException("listener is null");
        this.listeners.remove(listener);
    }
    
    public void unregisterAllListeners()
    {
        this.listeners.clear();
    }
    
    public synchronized void fireMusicEvent(MusicEvent event)
    {
        if (event == null)
            throw new NullPointerException("event should not be null");
        
        if (!this.listeners.isEmpty())
        {
            for (TinySoundEventListener listener : this.listeners)
                listener.onMusicEvent(event);
        }
    }
    
    public synchronized void fireSoundEvent(SoundEvent event)
    {
        if (event == null)
            throw new NullPointerException("event should not be null");
        
        if (!this.listeners.isEmpty())
        {
            for (TinySoundEventListener listener : this.listeners)
                listener.onSoundEvent(event);
        }
    }
}
