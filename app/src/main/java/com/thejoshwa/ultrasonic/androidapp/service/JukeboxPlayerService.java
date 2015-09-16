package com.thejoshwa.ultrasonic.androidapp.service;

import com.thejoshwa.ultrasonic.androidapp.audiofx.EqualizerController;
import com.thejoshwa.ultrasonic.androidapp.audiofx.VisualizerController;
import com.thejoshwa.ultrasonic.androidapp.domain.PlayerState;

import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.STARTED;

/**
 * Created by tthomas on 4/24/15.
 */
public class JukeboxPlayerService implements PlayerService
{
	private JukeboxService jukeboxService;
	private float currentVolume = 0;

	@Override
	public void init()
	{
		if (jukeboxService != null) {
			return;
		}
		jukeboxService = new JukeboxService();
		jukeboxService.start();
	}

	@Override
	public void pause()
	{
		jukeboxService.stop();
	}

	@Override
	public void stop()
	{
		jukeboxService.stop();
	}

	@Override
	public void start()
	{
		jukeboxService.start();
	}

	@Override
	public void destroy()
	{
		jukeboxService.stopJukeboxService();
	}

	@Override
	public void syncPlaylist()
	{
		jukeboxService.updatePlaylist();
	}

	@Override
	public void seek(int currentPlayingIndex, int position)
	{
		jukeboxService.skip(currentPlayingIndex, position / 1000);
	}

	@Override
	public void waitForPlayer()
	{
		// no-op
	}

	@Override
	public int getPosition()
	{
		return jukeboxService.getPositionSeconds() * 1000;
	}

	@Override
	public int getDuration()
	{
		return 0;
	}

	@Override
	public boolean getEqualizerAvailable()
	{
		return false;
	}

	@Override
	public EqualizerController getEqualizerController()
	{
		return null;
	}

	@Override
	public boolean getVisualizerAvailable()
	{
		return false;
	}

	@Override
	public VisualizerController getVisualizerController()
	{
		return null;
	}

	@Override
	public void notifyPlayerStateChange(PlayerState playerState)
	{

	}

	@Override
	public void reset()
	{

	}

	@Override
	public void setupNext(DownloadFile downloadFile)
	{

	}

	@Override
	public float getVolume()
	{
		return currentVolume;
	}

	@Override
	public void setVolume(float volume)
	{
		if (currentVolume == volume) {
			return;
		}
		boolean up = currentVolume < volume;
		jukeboxService.adjustVolume(up);
		currentVolume = volume;
	}

	@Override
	public void restore(DownloadFile currentPlaying, int currentPlayingIndex, int currentPlayingPosition, boolean autoPlay)
	{
		if (currentPlaying == null) {
			return;
		}
		if (autoPlay)
		{
			seek(currentPlayingIndex, currentPlayingPosition / 1000);
		}
	}

	@Override
	public void play(int currentPlayingIndex)
	{
		seek(MediaPlayer.getInstance().getCurrentPlayingIndex(), 0);
		MediaPlayer.getInstance().setPlayerState(STARTED);

	}


	@Override
	public void startJukeboxService(boolean jukeboxEnabled)
	{
		init();
		jukeboxService.setEnabled(jukeboxEnabled);

		if (jukeboxEnabled)
		{
			jukeboxService.startJukeboxService();

			reset();

			// TODO: Do we need to do this?
			// Cancel current download, if necessary.
			if (DownloadServiceImpl.getInstance().getCurrentDownloading() != null)
			{
				DownloadServiceImpl.getInstance().getCurrentDownloading().cancelDownload();
			}

		}
		else
		{
			jukeboxService.stopJukeboxService();
		}

	}

	@Override
	public boolean canPlayFile(DownloadFile file)
	{
		if (MediaPlayer.getInstance().isJukeboxEnabled())
		{
			return true;
		}
		return false;
	}

	@Override
	public boolean canDownload()
	{
		return false;
	}
}
