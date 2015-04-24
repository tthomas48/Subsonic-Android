package com.thejoshwa.ultrasonic.androidapp.service;

/**
 * Created by tthomas on 4/24/15.
 */
public interface PlayerService
{
	public void init(DownloadServiceImpl downloadService);

	public void pause();

	public void stop();

	public void start();

	public void destroy();

	public void updatePlaylist();

	public void waitForPlayer();

	public void seek(int currentPlayingIndex, int position);

	public void setVolume(float volume);

}
