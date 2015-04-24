package com.thejoshwa.ultrasonic.androidapp.service;

import android.content.Intent;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.os.PowerManager;
import android.util.Log;

import com.thejoshwa.ultrasonic.androidapp.audiofx.EqualizerController;
import com.thejoshwa.ultrasonic.androidapp.audiofx.VisualizerController;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.IDLE;

/**
 * Created by tthomas on 4/24/15.
 */
public class AndroidPlayerService implements PlayerService
{
	private MediaPlayer mediaPlayer;
	private MediaPlayer nextMediaPlayer;

	private static boolean equalizerAvailable;
	private static boolean visualizerAvailable;
	private EqualizerController equalizerController;
	private VisualizerController visualizerController;

	private int cachedPosition;

	static
	{
		try
		{
			EqualizerController.checkAvailable();
			equalizerAvailable = true;
		}
		catch (Throwable t)
		{
			equalizerAvailable = false;
		}
	}

	static
	{
		try
		{
			VisualizerController.checkAvailable();
			visualizerAvailable = true;
		}
		catch (Throwable t)
		{
			visualizerAvailable = false;
		}
	}


	@Override
	public void init(DownloadServiceImpl downloadService)
	{
		if (mediaPlayer != null)
		{
			mediaPlayer.release();
		}

		mediaPlayer = new MediaPlayer();
		mediaPlayer.setWakeMode(downloadService, PowerManager.PARTIAL_WAKE_LOCK);

		mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener()
		{
			@Override
			public boolean onError(MediaPlayer mediaPlayer, int what, int more)
			{
				handleError(new Exception(String.format("MediaPlayer error: %d (%d)", what, more)));
				return false;
			}
		});

		try
		{
			Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
			i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer.getAudioSessionId());
			i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, downloadService.getPackageName());
			downloadService.sendBroadcast(i);
		}
		catch (Throwable e)
		{
			// Froyo or lower
		}

		if (equalizerAvailable)
		{
			equalizerController = new EqualizerController(downloadService, mediaPlayer);
			if (!equalizerController.isAvailable())
			{
				equalizerController = null;
			}
			else
			{
				equalizerController.loadSettings();
			}
		}
		if (visualizerAvailable)
		{
			visualizerController = new VisualizerController(mediaPlayer);
			if (!visualizerController.isAvailable())
			{
				visualizerController = null;
			}
		}

	}

	@Override
	public void pause()
	{
		mediaPlayer.pause();
	}

	@Override
	public void stop()
	{
		mediaPlayer.pause();
	}

	@Override
	public void start()
	{
		mediaPlayer.start();
	}

	@Override
	public void destroy()
	{
		mediaPlayer.release();

		if (nextMediaPlayer != null)
		{
			nextMediaPlayer.release();
		}

		if (equalizerController != null)
		{
			equalizerController.release();
		}

		if (visualizerController != null)
		{
			visualizerController.release();
		}

		Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
		i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer.getAudioSessionId());
		i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
		sendBroadcast(i);

	}

	@Override
	public void waitForPlayer()
	{
		while (mediaPlayer == null)
		{
			Util.sleepQuietly(50L);
		}

	}

	@Override
	public void updatePlaylist()
	{
		// no-op currently
	}

	@Override
	public void seek(int currentPlayingIndex, int position)
	{
		mediaPlayer.seekTo(position);
		cachedPosition = position;
	}

	@Override
	public void setVolume(float volume)
	{
		if (mediaPlayer != null)
		{
			mediaPlayer.setVolume(volume, volume);
		}
	}

	private void handleError(Exception x)
	{
		Log.w(TAG, String.format("Media player error: %s", x), x);

		try
		{
			mediaPlayer.reset();
		}
		catch (Exception ex)
		{
			Log.w(TAG, String.format("Exception encountered when resetting media player: %s", ex), ex);
		}

		setPlayerState(IDLE);
	}

	private void handleErrorNext(Exception x)
	{
		Log.w(TAG, String.format("Next Media player error: %s", x), x);
		nextMediaPlayer.reset();
		setNextPlayerState(IDLE);
	}

}
