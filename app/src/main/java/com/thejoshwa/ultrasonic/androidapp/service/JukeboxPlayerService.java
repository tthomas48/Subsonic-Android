package com.thejoshwa.ultrasonic.androidapp.service;

/**
 * Created by tthomas on 4/24/15.
 */
public class JukeboxPlayerService implements PlayerService
{
	private JukeboxService jukeboxService;

	@Override
	public void init(DownloadServiceImpl downloadService)
	{
		jukeboxService = new JukeboxService(downloadService);
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
	public void updatePlaylist()
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
}
