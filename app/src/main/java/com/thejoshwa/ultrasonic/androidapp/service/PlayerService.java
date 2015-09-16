package com.thejoshwa.ultrasonic.androidapp.service;

import com.thejoshwa.ultrasonic.androidapp.audiofx.EqualizerController;
import com.thejoshwa.ultrasonic.androidapp.audiofx.VisualizerController;
import com.thejoshwa.ultrasonic.androidapp.domain.PlayerState;

/**
 * Created by tthomas on 4/24/15.
 */
public interface PlayerService
{
	public void init();

	public void pause();

	public void stop();

	public void start();

	public void destroy();

	public void syncPlaylist();

	public void waitForPlayer();

	public void seek(int currentPlayingIndex, int position);

	public float getVolume();

	public void setVolume(float volume);

	public void reset();

	public void setupNext(final DownloadFile downloadFile);

	public int getPosition();

	public int getDuration();

	public boolean getEqualizerAvailable();

	public EqualizerController getEqualizerController();

	public boolean getVisualizerAvailable();

	public VisualizerController getVisualizerController();

	public void notifyPlayerStateChange(PlayerState playerState);

	public void restore(DownloadFile currentPlaying, int currentPlayingIndex, int currentPlayingPosition, boolean autoPlay);

	public void play(int currentPlayingIndex);

	public void startJukeboxService(boolean jukeboxEnabled);

	public boolean canPlayFile(DownloadFile file);

	public boolean canDownload();
}

