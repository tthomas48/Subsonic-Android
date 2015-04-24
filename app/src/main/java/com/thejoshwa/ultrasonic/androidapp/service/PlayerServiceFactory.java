package com.thejoshwa.ultrasonic.androidapp.service;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;

import com.thejoshwa.ultrasonic.androidapp.activity.SubsonicTabActivity;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.PlayerState;
import com.thejoshwa.ultrasonic.androidapp.provider.UltraSonicAppWidgetProvider4x1;
import com.thejoshwa.ultrasonic.androidapp.provider.UltraSonicAppWidgetProvider4x2;
import com.thejoshwa.ultrasonic.androidapp.provider.UltraSonicAppWidgetProvider4x3;
import com.thejoshwa.ultrasonic.androidapp.provider.UltraSonicAppWidgetProvider4x4;
import com.thejoshwa.ultrasonic.androidapp.util.FileUtil;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.COMPLETED;
import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.DOWNLOADING;
import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.IDLE;
import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.PAUSED;
import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.PREPARING;
import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.STARTED;
import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.STOPPED;

/**
 * Created by tthomas on 4/24/15.
 */
public class PlayerServiceFactory
{
	private DownloadFile currentPlaying;
	private DownloadFile nextPlaying;

	private static final PlayerService ANDROID_PLAYER_SERVICE = new AndroidPlayerService();
	private static final PlayerService SPOTIFY_PLAYER_SERVICE = new SpotifyPlayerService();
	private static final PlayerService JUKEBOX_PLAYER_SERVICE = new JukeboxPlayerService();

	public static void init(DownloadServiceImpl downloadService) {
		ANDROID_PLAYER_SERVICE.init(downloadService);
		SPOTIFY_PLAYER_SERVICE.init(downloadService);
		JUKEBOX_PLAYER_SERVICE.init(downloadService);
	}

	public static PlayerService getPlayerService(Context context)
	{
		if (DownloadServiceImpl.getInstance().isJukeboxEnabled()) {
			return JUKEBOX_PLAYER_SERVICE;
		}
		//DownloadFile file = DownloadServiceImpl.getInstance().getCurrentPlaying();
		return ANDROID_PLAYER_SERVICE;
	}

	public static void destroy() {
		ANDROID_PLAYER_SERVICE.destroy();
		SPOTIFY_PLAYER_SERVICE.destroy();
		JUKEBOX_PLAYER_SERVICE.destroy();

	}

	public static void restore() {
		if (currentPlayingIndex != -1)
		{
			PlayerServiceFactory.getPlayerService(this).waitForPlayer();

			play(currentPlayingIndex, autoPlayStart);

			if (currentPlaying != null)
			{
				if (autoPlay && jukeboxEnabled)
				{
					jukeboxService.skip(getCurrentPlayingIndex(), currentPlayingPosition / 1000);
				}
				else
				{
					if (currentPlaying.isCompleteFileAvailable())
					{
						doPlay(currentPlaying, currentPlayingPosition, autoPlay);
					}
				}
			}

			autoPlayStart = false;
		}
	}

	@Override
	public synchronized void play(int index)
	{
		play(index, true);
	}

	private synchronized void play(int index, boolean start)
	{
		updateRemoteControl();

		if (index < 0 || index >= size())
		{
			resetPlayback();
		}
		else
		{
			if (nextPlayingTask != null)
			{
				nextPlayingTask.cancel();
				nextPlayingTask = null;
			}

			setCurrentPlaying(index);

			if (start)
			{
				if (jukeboxEnabled)
				{
					jukeboxService.skip(getCurrentPlayingIndex(), 0);
					setPlayerState(STARTED);
				}
				else
				{
					bufferAndPlay();
				}
			}

			checkDownloads();
			setNextPlaying();
		}
	}

	private synchronized void playNext()
	{
		MediaPlayer tmp = mediaPlayer;
		mediaPlayer = nextMediaPlayer;
		nextMediaPlayer = tmp;
		setCurrentPlaying(nextPlaying);
		setPlayerState(PlayerState.STARTED);
		setupHandlers(currentPlaying, false);
		setNextPlaying();

		// Proxy should not be being used here since the next player was already setup to play
		if (proxy != null)
		{
			proxy.stop();
			proxy = null;
		}
	}

	/**
	 * Plays either the current song (resume) or the first/next one in queue.
	 */
	public synchronized void play()
	{
		int current = getCurrentPlayingIndex();
		if (current == -1)
		{
			play(0);
		}
		else
		{
			play(current);
		}
	}
	@Override
	public synchronized void seekTo(int position)
	{
		try
		{
			PlayerServiceFactory.getPlayerService(this).seek(getCurrentPlayingIndex(), position);

			if (jukeboxEnabled)
			{

			}
			else
			{
			}
			updateRemoteControl();
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	@Override
	public synchronized void previous()
	{
		int index = getCurrentPlayingIndex();
		if (index == -1)
		{
			return;
		}

		// Restart song if played more than five seconds.
		if (getPlayerPosition() > 5000 || index == 0)
		{
			play(index);
		}
		else
		{
			play(index - 1);
		}
	}

	@Override
	public synchronized void next()
	{
		int index = getCurrentPlayingIndex();
		if (index != -1)
		{
			play(index + 1);
		}
	}

	private void onSongCompleted()
	{
		int index = getCurrentPlayingIndex();

		if (currentPlaying != null)
		{
			final MusicDirectory.Entry song = currentPlaying.getSong();

			if (song != null && song.getBookmarkPosition() > 0 && Util.getShouldClearBookmark(this))
			{
				MusicService musicService = MusicServiceFactory.getMusicService(DownloadServiceImpl.this);
				try
				{
					musicService.deleteBookmark(song.getId(), DownloadServiceImpl.this, null);
				}
				catch (Exception ignored)
				{

				}
			}
		}

		if (index != -1)
		{
			switch (getRepeatMode())
			{
				case OFF:
					if (index + 1 < 0 || index + 1 >= size())
					{
						if (Util.getShouldClearPlaylist(this))
						{
							clear();
						}

						resetPlayback();
						break;
					}

					play(index + 1);
					break;
				case ALL:
					play((index + 1) % size());
					break;
				case SINGLE:
					play(index);
					break;
				default:
					break;
			}
		}
	}

	@Override
	public synchronized void pause()
	{
		try
		{
			if (playerState == STARTED)
			{
				PlayerServiceFactory.getPlayerService(context).pause();
				setPlayerState(PAUSED);
			}
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	@Override
	public synchronized void stop()
	{
		try
		{
			if (playerState == STARTED)
			{
				PlayerServiceFactory.getPlayerService(this).stop();
				setPlayerState(STOPPED);
			}
			else if (playerState == PAUSED)
			{
				setPlayerState(STOPPED);
			}
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	@Override
	public synchronized void start()
	{
		try
		{
			PlayerServiceFactory.getPlayerService(this).start();
			setPlayerState(STARTED);
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	@Override
	public synchronized void reset()
	{
		if (bufferTask != null)
		{
			bufferTask.cancel();
		}
		try
		{
			setPlayerState(IDLE);
			mediaPlayer.setOnErrorListener(null);
			mediaPlayer.setOnCompletionListener(null);
			mediaPlayer.reset();
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	@Override
	public synchronized int getPlayerPosition()
	{
		try
		{
			if (playerState == IDLE || playerState == DOWNLOADING || playerState == PREPARING)
			{
				return 0;
			}

			return jukeboxEnabled ? jukeboxService.getPositionSeconds() * 1000 : cachedPosition;
		}
		catch (Exception x)
		{
			handleError(x);
			return 0;
		}
	}

	@Override
	public synchronized int getPlayerDuration()
	{
		if (currentPlaying != null)
		{
			Integer duration = currentPlaying.getSong().getDuration();
			if (duration != null)
			{
				return duration * 1000;
			}
		}
		if (playerState != IDLE && playerState != DOWNLOADING && playerState != PlayerState.PREPARING)
		{
			try
			{
				return mediaPlayer.getDuration();
			}
			catch (Exception x)
			{
				handleError(x);
			}
		}
		return 0;
	}

	@Override
	public PlayerState getPlayerState()
	{
		return playerState;
	}

	synchronized void setPlayerState(PlayerState playerState)
	{
		Log.i(TAG, String.format("%s -> %s (%s)", this.playerState.name(), playerState.name(), currentPlaying));

		this.playerState = playerState;

		if (this.playerState == PAUSED)
		{
			lifecycleSupport.serializeDownloadQueue();
		}

		if (this.playerState == PlayerState.STARTED)
		{
			Util.requestAudioFocus(this);
		}

		boolean showWhenPaused = (this.playerState != PlayerState.STOPPED && Util.isNotificationAlwaysEnabled(this));
		boolean show = this.playerState == PlayerState.STARTED || showWhenPaused;

		Util.broadcastPlaybackStatusChange(this, this.playerState);
		Util.broadcastA2dpPlayStatusChange(this, this.playerState, instance);

		if (this.playerState == PlayerState.STARTED || this.playerState == PlayerState.PAUSED)
		{
			// Set remote control
			updateRemoteControl();
		}

		// Update widget
		UltraSonicAppWidgetProvider4x1.getInstance().notifyChange(this, this, this.playerState == PlayerState.STARTED, false);
		UltraSonicAppWidgetProvider4x2.getInstance().notifyChange(this, this, this.playerState == PlayerState.STARTED, true);
		UltraSonicAppWidgetProvider4x3.getInstance().notifyChange(this, this, this.playerState == PlayerState.STARTED, false);
		UltraSonicAppWidgetProvider4x4.getInstance().notifyChange(this, this, this.playerState == PlayerState.STARTED, false);
		SubsonicTabActivity tabInstance = SubsonicTabActivity.getInstance();

		MusicDirectory.Entry song = null;

		if (currentPlaying != null)
		{
			song = currentPlaying.getSong();
		}

		if (show)
		{
			if (tabInstance != null)
			{
				if (SubsonicTabActivity.currentSong != song)
				{
					int size = Util.getNotificationImageSize(this);
					tabInstance.nowPlayingImage = FileUtil.getAlbumArtBitmap(this, song, size, true);
				}

				// Only update notification is player state is one that will change the icon
				if (this.playerState == PlayerState.STARTED || this.playerState == PlayerState.PAUSED)
				{
					tabInstance.showNotification(handler, song, this, this.notification, this.playerState);
					tabInstance.showNowPlaying();
				}
			}
		}
		else
		{
			if (tabInstance != null)
			{
				tabInstance.nowPlayingImage = null;
				tabInstance.hidePlayingNotification(handler, this);
				tabInstance.hideNowPlaying();
			}
		}

		if (this.playerState == STARTED)
		{
			scrobbler.scrobble(this, currentPlaying, false);
		}
		else if (this.playerState == COMPLETED)
		{
			scrobbler.scrobble(this, currentPlaying, true);
		}

		if (playerState == STARTED && positionCache == null)
		{
			positionCache = new PositionCache();
			Thread thread = new Thread(positionCache);
			thread.start();
		}
		else if (playerState != STARTED && positionCache != null)
		{
			positionCache.stop();
			positionCache = null;
		}
	}

	private void setPlayerStateCompleted()
	{
		Log.i(TAG, String.format("%s -> %s (%s)", this.playerState.name(), PlayerState.COMPLETED, currentPlaying));
		this.playerState = PlayerState.COMPLETED;

		if (positionCache != null)
		{
			positionCache.stop();
			positionCache = null;
		}

		scrobbler.scrobble(this, currentPlaying, true);
	}

	private synchronized void setNextPlayerState(PlayerState playerState)
	{
		Log.i(TAG, String.format("Next: %s -> %s (%s)", this.nextPlayerState.name(), playerState.name(), nextPlaying));
		this.nextPlayerState = playerState;
	}


}
