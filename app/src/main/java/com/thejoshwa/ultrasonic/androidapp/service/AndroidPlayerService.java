package com.thejoshwa.ultrasonic.androidapp.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.widget.SeekBar;

import com.thejoshwa.ultrasonic.androidapp.activity.DownloadActivity;
import com.thejoshwa.ultrasonic.androidapp.audiofx.EqualizerController;
import com.thejoshwa.ultrasonic.androidapp.audiofx.VisualizerController;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.PlayerState;
import com.thejoshwa.ultrasonic.androidapp.util.CancellableTask;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.StreamProxy;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import java.io.File;
import java.net.URLEncoder;

import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.DOWNLOADING;
import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.IDLE;
import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.PAUSED;
import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.PREPARED;
import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.PREPARING;
import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.STARTED;

/**
 * Created by tthomas on 4/24/15.
 */
public class AndroidPlayerService implements PlayerService
{
	private static final String TAG = AndroidPlayerService.class.getSimpleName();

	private CancellableTask bufferTask;

	private MediaPlayer androidPlayer;
	private MediaPlayer nextAndroidPlayer;

	private static boolean equalizerAvailable;
	private static boolean visualizerAvailable;
	private EqualizerController equalizerController;
	private VisualizerController visualizerController;

	private PositionCache positionCache;
	private int cachedPosition;
	private StreamProxy proxy;

	private float currentVolume = 0;
	private int secondaryProgress = -1;
	private boolean nextSetup;

	private PowerManager.WakeLock wakeLock;

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
	public void init()
	{
		if (androidPlayer != null)
		{
			androidPlayer.release();
		}

		com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer player = com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance();

		androidPlayer = new MediaPlayer();
		androidPlayer.setWakeMode(player, PowerManager.PARTIAL_WAKE_LOCK);

		androidPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener()
		{
			@Override
			public boolean onError(MediaPlayer mediaPlayer, int what, int more)
			{
				com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().handleError(new Exception(String.format("MediaPlayer error: %d (%d)", what, more)));
				return false;
			}
		});

		try
		{
			Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
			i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, androidPlayer.getAudioSessionId());
			i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, player.getPackageName());
			player.sendBroadcast(i);
		}
		catch (Throwable e)
		{
			// Froyo or lower
		}

		if (equalizerAvailable)
		{
			equalizerController = new EqualizerController(player, androidPlayer);
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
			visualizerController = new VisualizerController(androidPlayer);
			if (!visualizerController.isAvailable())
			{
				visualizerController = null;
			}
		}

		PowerManager pm = (PowerManager) player.getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
		wakeLock.setReferenceCounted(false);
	}



	@Override
	public void pause()
	{
		androidPlayer.pause();
	}

	@Override
	public void stop()
	{
		androidPlayer.pause();
	}

	@Override
	public void start()
	{
		androidPlayer.start();
	}

	@Override
	public void destroy()
	{
		androidPlayer.release();

		if (nextAndroidPlayer != null)
		{
			nextAndroidPlayer.release();
		}

		if (equalizerController != null)
		{
			equalizerController.release();
		}

		if (visualizerController != null)
		{
			visualizerController.release();
		}

		if (bufferTask != null)
		{
			bufferTask.cancel();
		}

		wakeLock.release();

		Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
		i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, androidPlayer.getAudioSessionId());
		i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().getPackageName());
		com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().sendBroadcast(i);
	}

	@Override
	public void waitForPlayer()
	{
		while (androidPlayer == null)
		{
			Util.sleepQuietly(50L);
		}

	}

	@Override
	public void seek(int currentPlayingIndex, int position)
	{
		androidPlayer.seekTo(position);
		cachedPosition = position;
	}

	@Override
	public float getVolume() {
		return currentVolume;
	}



	@Override
	public void setVolume(float volume)
	{
		if (androidPlayer != null)
		{
			currentVolume = volume;
			androidPlayer.setVolume(volume, volume);
		}
	}


	public void reset() {

		if (androidPlayer != null && androidPlayer.isPlaying()) {
			androidPlayer.stop();
		}

		if (bufferTask != null)
		{
			bufferTask.cancel();
		}

		try
		{
			if (androidPlayer != null)
			{
				androidPlayer.setOnErrorListener(null);
				androidPlayer.setOnCompletionListener(null);
			}
		}
		catch (Exception x)
		{
			com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().handleError(x);
		}

	}

	public synchronized void setupNext(final DownloadFile downloadFile)
	{
		try
		{
			final File file = downloadFile.isCompleteFileAvailable() ? downloadFile.getCompleteFile() : downloadFile.getPartialFile();

			if (nextAndroidPlayer != null)
			{
				nextAndroidPlayer.setOnCompletionListener(null);
				nextAndroidPlayer.release();
				nextAndroidPlayer = null;
			}

			nextAndroidPlayer = new android.media.MediaPlayer();
			nextAndroidPlayer.setWakeMode(com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance(), PowerManager.PARTIAL_WAKE_LOCK);

			try
			{
				nextAndroidPlayer.setAudioSessionId(androidPlayer.getAudioSessionId());
			}
			catch (Throwable e)
			{
				nextAndroidPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			}

			nextAndroidPlayer.setDataSource(file.getPath());
			com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().setNextPlayerState(PREPARING);

			nextAndroidPlayer.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener()
			{
				@Override
				@SuppressLint("NewApi")
				public void onPrepared(android.media.MediaPlayer mp)
				{
					try
					{
						getMediaPlayer().setNextPlayerState(PREPARED);

						if (Util.getGaplessPlaybackPreference(getMediaPlayer()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && (getMediaPlayer().getPlayerState() == PlayerState.STARTED || getMediaPlayer().getPlayerState() == PlayerState.PAUSED))
						{
							androidPlayer.setNextMediaPlayer(nextAndroidPlayer);
							nextSetup = true;
						}
					} catch (Exception x)
					{
						handleErrorNext(x);
					}
				}
			});

			nextAndroidPlayer.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener()
			{
				@Override
				public boolean onError(android.media.MediaPlayer mediaPlayer, int what, int extra)
				{
					Log.w(TAG, String.format("Error on playing next (%d, %d): %s", what, extra, downloadFile));
					return true;
				}
			});

			nextAndroidPlayer.prepareAsync();
		}
		catch (Exception x)
		{
			handleErrorNext(x);
		}
	}

	private void setupHandlers(final DownloadFile downloadFile, final boolean isPartial)
	{
		androidPlayer.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener()
		{
			@Override
			public boolean onError(android.media.MediaPlayer mediaPlayer, int what, int extra)
			{
				Log.w(TAG, String.format("Error on playing file (%d, %d): %s", what, extra, downloadFile));
				int pos = cachedPosition;
				reset();
				downloadFile.setPlaying(false);
				doPlay(downloadFile, pos, true);
				downloadFile.setPlaying(true);
				return true;
			}
		});

		final int duration = downloadFile.getSong().getDuration() == null ? 0 : downloadFile.getSong().getDuration() * 1000;

		androidPlayer.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener()
		{
			@Override
			public void onCompletion(android.media.MediaPlayer mediaPlayer)
			{
				// Acquire a temporary wakelock, since when we return from
				// this callback the MediaPlayer will release its wakelock
				// and allow the device to go to sleep.
				wakeLock.acquire(60000);

				int pos = cachedPosition;
				Log.i(TAG, String.format("Ending position %d of %d", pos, duration));

				if (!isPartial || (downloadFile.isWorkDone() && (Math.abs(duration - pos) < 1000)))
				{
					getMediaPlayer().setPlayerStateCompleted();

					if (Util.getGaplessPlaybackPreference(getMediaPlayer()) && getMediaPlayer().getNextPlaying() != null && getMediaPlayer().getNextPlayerState() == PlayerState.PREPARED)
					{
						if (!nextSetup)
						{
							playNext();
						} else
						{
							nextSetup = false;
							playNext();
						}
					} else
					{
						getMediaPlayer().onSongCompleted();
					}

					return;
				}

				synchronized (getMediaPlayer())
				{
					if (downloadFile.isWorkDone())
					{
						// Complete was called early even though file is fully buffered
						Log.i(TAG, String.format("Requesting restart from %d of %d", pos, duration));
						reset();
						downloadFile.setPlaying(false);
						doPlay(downloadFile, pos, true);
						downloadFile.setPlaying(true);
					} else
					{
						Log.i(TAG, String.format("Requesting restart from %d of %d", pos, duration));
						reset();
						bufferTask = new BufferTask(downloadFile, pos);
						bufferTask.start();
					}
				}
			}
		});
	}
	private synchronized void playNext()
	{
		android.media.MediaPlayer tmp = androidPlayer;
		androidPlayer = nextAndroidPlayer;
		nextAndroidPlayer = tmp;
		getMediaPlayer().setCurrentPlaying(getMediaPlayer().getNextPlaying());
		getMediaPlayer().setPlayerState(PlayerState.STARTED);
		setupHandlers(getMediaPlayer().getCurrentPlaying(), false);
		getMediaPlayer().setNextPlaying();

		// Proxy should not be being used here since the next player was already setup to play
		if (proxy != null)
		{
			proxy.stop();
			proxy = null;
		}
	}

	@Override
	public void syncPlaylist()
	{

	}

	private void handleErrorNext(Exception x)
	{
		Log.w(TAG, String.format("Next Media player error: %s", x), x);
		nextAndroidPlayer.reset();
		getMediaPlayer().setNextPlayerState(IDLE);
	}

	@Override
	public int getPosition()
	{
		return cachedPosition;
	}

	@Override
	public int getDuration()
	{
		return androidPlayer.getDuration();
	}

	@Override
	public boolean getEqualizerAvailable()
	{
		return equalizerAvailable;
	}

	@Override
	public boolean getVisualizerAvailable()
	{
		return visualizerAvailable;
	}

	@Override
	public EqualizerController getEqualizerController()
	{
		if (equalizerAvailable && equalizerController == null)
		{
			equalizerController = new EqualizerController(getMediaPlayer(), androidPlayer);
			if (!equalizerController.isAvailable())
			{
				equalizerController = null;
			}
			else
			{
				equalizerController.loadSettings();
			}
		}
		return equalizerController;

	}

	@Override
	public VisualizerController getVisualizerController()
	{
		if (visualizerAvailable && visualizerController == null)
		{
			visualizerController = new VisualizerController(androidPlayer);
			if (!visualizerController.isAvailable())
			{
				visualizerController = null;
			}
		}
		return visualizerController;

	}

	private class PositionCache implements Runnable
	{
		boolean isRunning = true;

		public void stop()
		{
			isRunning = false;
		}

		@Override
		public void run()
		{
			Thread.currentThread().setName("PositionCache");

			// Stop checking position before the song reaches completion
			while (isRunning)
			{
				try
				{
					if (androidPlayer != null && com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().getPlayerState() == STARTED)
					{
						cachedPosition = androidPlayer.getCurrentPosition();
					}

					Util.sleepQuietly(25L);
				}
				catch (Exception e)
				{
					Log.w(TAG, "Crashed getting current position", e);
					isRunning = false;
					positionCache = null;
				}
			}
		}
	}

	@Override
	public void notifyPlayerStateChange(PlayerState playerState)
	{
		if (playerState == STARTED && positionCache == null)
		{
			positionCache = new PositionCache();
			Thread thread = new Thread(positionCache);
			thread.start();
			return;
		}

		if (playerState != STARTED && positionCache != null)
		{
			positionCache.stop();
			positionCache = null;
		}
	}

	protected synchronized void doPlay(final DownloadFile downloadFile, final int position, final boolean start)
	{
		try
		{
			com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer player = com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance();
			downloadFile.setPlaying(false);
			//downloadFile.setPlaying(true);
			final File file = downloadFile.isCompleteFileAvailable() ? downloadFile.getCompleteFile() : downloadFile.getPartialFile();
			boolean partial = file.equals(downloadFile.getPartialFile());
			downloadFile.updateModificationDate();

			androidPlayer.setOnCompletionListener(null);
			secondaryProgress = -1; // Ensure seeking in non StreamProxy playback works
			androidPlayer.reset();
			player.setPlayerState(IDLE);
			androidPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			String dataSource = file.getPath();

			if (partial)
			{
				if (proxy == null)
				{
					proxy = new StreamProxy(DownloadServiceImpl.getInstance());
					proxy.start();
				}

				dataSource = String.format("http://127.0.0.1:%d/%s", proxy.getPort(), URLEncoder.encode(dataSource, Constants.UTF_8));
				Log.i(TAG, String.format("Data Source: %s", dataSource));
			}
			else if (proxy != null)
			{
				proxy.stop();
				proxy = null;
			}

			Log.i(TAG, "Preparing media player");
			androidPlayer.setDataSource(dataSource);
			getMediaPlayer().setPlayerState(PREPARING);

			androidPlayer.setOnBufferingUpdateListener(new android.media.MediaPlayer.OnBufferingUpdateListener()
			{
				@Override
				public void onBufferingUpdate(android.media.MediaPlayer mp, int percent)
				{
					SeekBar progressBar = DownloadActivity.getProgressBar();
					MusicDirectory.Entry song = downloadFile.getSong();

					if (percent == 100)
					{
						if (progressBar != null)
						{
							progressBar.setSecondaryProgress(100 * progressBar.getMax());
						}

						mp.setOnBufferingUpdateListener(null);
					} else if (progressBar != null && song.getTranscodedContentType() == null && Util.getMaxBitRate(getMediaPlayer()) == 0)
					{
						secondaryProgress = (int) (((double) percent / (double) 100) * progressBar.getMax());
						progressBar.setSecondaryProgress(secondaryProgress);
					}
				}
			});

			androidPlayer.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener()
			{
				@Override
				public void onPrepared(android.media.MediaPlayer mp)
				{
					Log.i(TAG, "Media player prepared");

					com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().setPlayerState(PREPARED);

					SeekBar progressBar = DownloadActivity.getProgressBar();

					if (progressBar != null && downloadFile.isWorkDone())
					{
						// Populate seek bar secondary progress if we have a complete file for consistency
						DownloadActivity.getProgressBar().setSecondaryProgress(100 * progressBar.getMax());
					}

					synchronized (com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance())
					{
						if (position != 0)
						{
							Log.i(TAG, String.format("Restarting player from position %d", position));
							com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().seekTo(position);
						}
						cachedPosition = position;

						if (start)
						{
							androidPlayer.start();
							com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().setPlayerState(STARTED);
						} else
						{
							com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().setPlayerState(PAUSED);
						}
					}

					DownloadServiceImpl.getInstance().serializeDownloadQueue();
				}
			});

			setupHandlers(downloadFile, partial);

			androidPlayer.prepareAsync();
		}
		catch (Exception x)
		{
			com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().handleError(x);
		}
	}

	private synchronized void bufferAndPlay()
	{
		DownloadFile currentPlaying = com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().getCurrentPlaying();
		if (com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().getPlayerState() != PREPARED)
		{
			reset();

			bufferTask = new BufferTask(currentPlaying, 0);
			bufferTask.start();
		}
		else
		{
			doPlay(currentPlaying, 0, true);
		}
	}

	private class BufferTask extends CancellableTask
	{
		private final DownloadFile downloadFile;
		private final int position;
		private final long expectedFileSize;
		private final File partialFile;

		public BufferTask(DownloadFile downloadFile, int position)
		{
			this.downloadFile = downloadFile;
			this.position = position;
			partialFile = downloadFile.getPartialFile();

			long bufferLength = Util.getBufferLength(com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance());

			if (bufferLength == 0)
			{
				// Set to seconds in a day, basically infinity
				bufferLength = 86400L;
			}

			// Calculate roughly how many bytes BUFFER_LENGTH_SECONDS corresponds to.
			int bitRate = downloadFile.getBitRate();
			long byteCount = Math.max(100000, bitRate * 1024L / 8L * bufferLength);

			// Find out how large the file should grow before resuming playback.
			Log.i(TAG, String.format("Buffering from position %d and bitrate %d", position, bitRate));
			expectedFileSize = (position * bitRate / 8) + byteCount;
		}

		@Override
		public void execute()
		{
			com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance().setPlayerState(DOWNLOADING);

			while (!bufferComplete() && !Util.isOffline(com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance()))
			{
				Util.sleepQuietly(1000L);
				if (isCancelled())
				{
					return;
				}
			}
			doPlay(downloadFile, position, true);
		}

		private boolean bufferComplete()
		{
			boolean completeFileAvailable = downloadFile.isWorkDone();
			long size = partialFile.length();

			Log.i(TAG, String.format("Buffering %s (%d/%d, %s)", partialFile, size, expectedFileSize, completeFileAvailable));
			return completeFileAvailable || size >= expectedFileSize;
		}

		@Override
		public String toString()
		{
			return String.format("BufferTask (%s)", downloadFile);
		}
	}

	@Override
	public void restore(DownloadFile currentPlaying, int currentPlayingIndex, int currentPlayingPosition, boolean autoPlay)
	{
		if (currentPlaying == null) {
			return;
		}
		if (currentPlaying.isCompleteFileAvailable())
		{
			doPlay(currentPlaying, currentPlayingPosition, autoPlay);
		}

	}

	@Override
	public void play(int currentPlayingIndex)
	{
		bufferAndPlay();
	}

	@Override
	public void startJukeboxService(boolean jukeboxEnabled)
	{

	}

	private com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer getMediaPlayer() {
		return com.thejoshwa.ultrasonic.androidapp.service.MediaPlayer.getInstance();
	}

	@Override
	public boolean canPlayFile(DownloadFile file)
	{
		return true;
	}

	@Override
	public boolean canDownload()
	{
		return true;
	}

}
