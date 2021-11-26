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
package kuusisto.tinysound.event;

/**
 * An event listener to use to listen all musics and sound interaction.
 * 
 * <p>
 * This event listener allow to handle when a music begin to start, or will be
 * stopped (or paused). It can also handle when sound will be played and when
 * they done.
 * </p>
 * 
 * @author DrogoniEntity
 */
public interface TinySoundEventListener
{
    
    /**
     * Invoked when a interaction has been done on a music.
     * 
     * <p>
     * Information about which music has been handled and what is its new statement
     * are given by {@code event}.
     * </p>
     * 
     * @param event
     *            fired event
     */
    void onMusicEvent(MusicEvent event);
    
    /**
     * Invoked when a interaction has been done on a sound.
     * 
     * <p>
     * Information about which sound has been handled and what is its new statement
     * are given by {@code event}.
     * </p>
     * 
     * @param event
     *            fired event
     */
    void onSoundEvent(SoundEvent event);
}
