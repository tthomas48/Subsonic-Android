package com.thejoshwa.ultrasonic.androidapp.service;

import android.util.Log;

import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.PlayerStateCallback;
import com.spotify.sdk.android.player.Spotify;
import com.thejoshwa.ultrasonic.androidapp.audiofx.EqualizerController;
import com.thejoshwa.ultrasonic.androidapp.audiofx.VisualizerController;

public class SpotifyPlayerService implements PlayerNotificationCallback, PlayerService, PlayerStateCallback
{

	private static final String TAG = SpotifyPlayerService.class.getSimpleName();


	private static Config playerConfig;
	private Player mPlayer;
	private int lastPosition;
	private int duration;
	private boolean observing;

	@Override
	public void syncPlaylist()
	{

	}

	private boolean gotStarted;

	@Override
	public void init()
	{
		new Thread(new Runnable() {
			@Override
			public void run()
			{
				Thread.currentThread().setName("SpotifyPlayerServiceInit");
				while(playerConfig == null) {
					try
					{
						Thread.sleep(500);
					} catch(InterruptedException e) {
						Log.e(TAG, "Spotify interrupted while initializing", e);
						return;
					}
				}

				observing = true;
				if (mPlayer != null) {
					return;
				}

				mPlayer = Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver()
				{
					@Override
					public void onInitialized(Player player)
					{
						mPlayer.addPlayerNotificationCallback(SpotifyPlayerService.this);
					}

					@Override
					public void onError(Throwable throwable)
					{
						Log.e(TAG, "Could not initialize spotify player: " + throwable.getMessage());
					}
				});
			}
		}).start();
	}

	@Override
	public void onPlaybackEvent(EventType eventType, PlayerState playerState)
	{

		Log.d(TAG, "Playback event received: " + eventType.name());
		if (!observing) {
			Log.d(TAG, "Not currently observing events");
			return;
		}
		switch (eventType) {
			// Handle event type as necessary
			case TRACK_END:
				if (!gotStarted) {
					break;
				}
				MediaPlayer.getInstance().setPlayerState(com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.COMPLETED);
				MediaPlayer.getInstance().onSongCompleted();
				break;
			case PLAY:
				MediaPlayer.getInstance().setPlayerState(com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.STARTED);
				gotStarted = true;
				break;
			case PAUSE:
				MediaPlayer.getInstance().setPlayerState(com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.PAUSED);
				break;

				// TODO: We need to let it know the song is done
			default:
				break;
		}
	}

	@Override
	public void onPlaybackError(ErrorType errorType, String s)
	{
		Log.d(TAG, "Playback error received: " + errorType.name());
		switch (errorType) {
			// Handle event type as necessary
			default:
				break;
		}

	}

	@Override
	public void waitForPlayer()
	{

	}

	@Override
	public void seek(int currentPlayingIndex, int position)
	{
		mPlayer.seekToPosition(position);

	}

	@Override
	public float getVolume()
	{
		return 0;
	}

	@Override
	public void setVolume(float volume)
	{
	}

	@Override
	public void reset()
	{
		observing = false;
		if (mPlayer != null)
		{
			mPlayer.pause();
		}
	}

	@Override
	public void setupNext(DownloadFile downloadFile)
	{

	}

	@Override
	public int getPosition()
	{
		refreshPlayerState();
		return lastPosition;
	}

	@Override
	public int getDuration()
	{
		refreshPlayerState();
		return duration;
	}

	private void refreshPlayerState() {
		if (MediaPlayer.getInstance().getPlayerState() != com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.STARTED) {
			return;
		}
		if (mPlayer != null) {
			mPlayer.getPlayerState(this);
		}

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
	public void notifyPlayerStateChange(com.thejoshwa.ultrasonic.androidapp.domain.PlayerState playerState)
	{

	}

	@Override
	public void restore(DownloadFile currentPlaying, int currentPlayingIndex, int currentPlayingPosition, boolean autoPlay)
	{

	}

	@Override
	public void play(int currentPlayingIndex)
	{
		Log.d(TAG, "Currently attempting to play " + currentPlayingIndex);
		if (mPlayer == null) {
			return;
		}
		observing = true;
		gotStarted = false;

		if (MediaPlayer.getInstance().getPlayerState() == com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.PAUSED) {
			mPlayer.resume();
			return;
		}
		DownloadFile downloadFile = MediaPlayer.getInstance().getPlayQueue().get(currentPlayingIndex);
		String spotifyPath = downloadFile.getSong().getPath().replaceFirst("spotify:/", "");
		Log.d(TAG, "Currently attempting to play path " + spotifyPath);
		mPlayer.play(spotifyPath);
		refreshPlayerState();
	}

	@Override
	public void startJukeboxService(boolean jukeboxEnabled)
	{

	}

	@Override
	public void destroy()
	{
		Spotify.destroyPlayer(this);
	}

	@Override
	public void start()
	{
		play(MediaPlayer.getInstance().getCurrentPlayingIndex());
	}

	@Override
	public void pause()
	{

		//mPlayer.shutdown();
		mPlayer.pause();
	}

	@Override
	public void stop()
	{
		mPlayer.pause();
	}

	@Override
	public boolean canPlayFile(DownloadFile file)
	{
		if (file == null) {
			return false;
		}

		if (file.getSong().getPath().startsWith("spotify:/")) {
			return true;
		}
		return false;
	}

	public static void setPlayerConfig(Config playerConfig)
	{
		SpotifyPlayerService.playerConfig = playerConfig;
	}

	public static Config getPlayerConfig() {
		return SpotifyPlayerService.playerConfig;
	}

	@Override
	public void onPlayerState(PlayerState playerState)
	{
		lastPosition = playerState.positionInMs;
		duration = playerState.durationInMs;
	}

	@Override
	public boolean canDownload()
	{
		return false;
	}

}
