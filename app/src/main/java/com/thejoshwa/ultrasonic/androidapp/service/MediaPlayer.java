package com.thejoshwa.ultrasonic.androidapp.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.activity.DownloadActivity;
import com.thejoshwa.ultrasonic.androidapp.activity.SubsonicTabActivity;
import com.thejoshwa.ultrasonic.androidapp.audiofx.EqualizerController;
import com.thejoshwa.ultrasonic.androidapp.audiofx.VisualizerController;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.PlayerState;
import com.thejoshwa.ultrasonic.androidapp.domain.RepeatMode;
import com.thejoshwa.ultrasonic.androidapp.domain.UserInfo;
import com.thejoshwa.ultrasonic.androidapp.provider.UltraSonicAppWidgetProvider4x1;
import com.thejoshwa.ultrasonic.androidapp.provider.UltraSonicAppWidgetProvider4x2;
import com.thejoshwa.ultrasonic.androidapp.provider.UltraSonicAppWidgetProvider4x3;
import com.thejoshwa.ultrasonic.androidapp.provider.UltraSonicAppWidgetProvider4x4;
import com.thejoshwa.ultrasonic.androidapp.receiver.MediaButtonIntentReceiver;
import com.thejoshwa.ultrasonic.androidapp.util.CancellableTask;
import com.thejoshwa.ultrasonic.androidapp.util.FileUtil;
import com.thejoshwa.ultrasonic.androidapp.util.ShufflePlayBuffer;
import com.thejoshwa.ultrasonic.androidapp.util.SimpleServiceBinder;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
public class MediaPlayer extends Service
{
	private static final String TAG = MediaPlayer.class.getSimpleName();

	private final List<DownloadFile> playQueue = new ArrayList<DownloadFile>();


	private final IBinder binder = new SimpleServiceBinder<MediaPlayer>(this);
	private Notification notification = new Notification(R.drawable.ic_stat_ultrasonic, null, System.currentTimeMillis());
	private final MediaPlayerLifecycleSupport lifecycleSupport = new MediaPlayerLifecycleSupport(this);
	public RemoteControlClient remoteControlClient;
	private final ShufflePlayBuffer shufflePlayBuffer = new ShufflePlayBuffer(this);


	public static final String CMD_PLAY = "com.thejoshwa.ultrasonic.androidapp.CMD_PLAY";
	public static final String CMD_TOGGLEPAUSE = "com.thejoshwa.ultrasonic.androidapp.CMD_TOGGLEPAUSE";
	public static final String CMD_PAUSE = "com.thejoshwa.ultrasonic.androidapp.CMD_PAUSE";
	public static final String CMD_STOP = "com.thejoshwa.ultrasonic.androidapp.CMD_STOP";
	public static final String CMD_PREVIOUS = "com.thejoshwa.ultrasonic.androidapp.CMD_PREVIOUS";
	public static final String CMD_NEXT = "com.thejoshwa.ultrasonic.androidapp.CMD_NEXT";

	private AudioManager audioManager;
	private final Scrobbler scrobbler = new Scrobbler();


	private final Handler handler = new Handler();
	private Looper mediaPlayerLooper;
	private Handler mediaPlayerHandler;
	private CancellableTask nextPlayingTask;


	private boolean showVisualization;

	private DownloadFile currentPlaying;
	private DownloadFile nextPlaying;
	private PlayerState playerState = IDLE;
	private PlayerState nextPlayerState = IDLE;
	private boolean jukeboxEnabled;
	private boolean keepScreenOn;
	private boolean autoPlayStart;
	private boolean nextSetup;
	private boolean shufflePlay;
	private long revision;


	private static final PlayerService ANDROID_PLAYER_SERVICE = new AndroidPlayerService();
	private static final PlayerService SPOTIFY_PLAYER_SERVICE = new SpotifyPlayerService();
	private static final PlayerService JUKEBOX_PLAYER_SERVICE = new JukeboxPlayerService();

	private static MediaPlayer instance;

	@Override
	public void onCreate()
	{
		super.onCreate();

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
					Thread.currentThread().setName("MediaPlayer");

					Looper.prepare();

					instance = MediaPlayer.this;
					init();
					Looper.loop();
			}
		}).start();



		notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
		notification.contentView = new RemoteViews(this.getPackageName(), R.layout.notification);
		Util.linkButtons(this, notification.contentView, false);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
		{
			notification.bigContentView = new RemoteViews(this.getPackageName(), R.layout.notification_large);
			Util.linkButtons(this, notification.bigContentView, false);
		}

		Intent notificationIntent = new Intent(this, DownloadActivity.class);
		notification.contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		lifecycleSupport.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		super.onStartCommand(intent, flags, startId);
		lifecycleSupport.onStart(intent);
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		try
		{
			instance = null;
			lifecycleSupport.onDestroy();

			if (nextPlayingTask != null)
			{
				nextPlayingTask.cancel();
			}
			shufflePlayBuffer.shutdown();

			notification = null;
			shutdown();
		}
		catch (Throwable ignored)
		{
		}
	}


	private void init() {
		mediaPlayerLooper = Looper.myLooper();
		mediaPlayerHandler = new Handler(mediaPlayerLooper);

		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		setUpRemoteControlClient();

		ANDROID_PLAYER_SERVICE.init();
		SPOTIFY_PLAYER_SERVICE.init();
		JUKEBOX_PLAYER_SERVICE.init();

	}

	public void shutdown() {
		mediaPlayerLooper.quit();
		MediaPlayer.getPlayerService().destroy();
		audioManager.unregisterRemoteControlClient(remoteControlClient);
		clearRemoteControl();
	}

	public static MediaPlayer getInstance() {
			return instance;
	}

	private static PlayerService getPlayerService()
	{
		DownloadFile currentPlaying = MediaPlayer.getInstance().getCurrentPlaying();
		return getPlayerService(currentPlaying);
	}

	protected static PlayerService getPlayerService(DownloadFile currentPlaying) {

		if (JUKEBOX_PLAYER_SERVICE.canPlayFile(currentPlaying)) {
			return JUKEBOX_PLAYER_SERVICE;
		}
		if (SPOTIFY_PLAYER_SERVICE.canPlayFile(currentPlaying)) {
			return SPOTIFY_PLAYER_SERVICE;
		}
		return ANDROID_PLAYER_SERVICE;
	}

	public static void destroy() {
		ANDROID_PLAYER_SERVICE.destroy();
		SPOTIFY_PLAYER_SERVICE.destroy();
		JUKEBOX_PLAYER_SERVICE.destroy();

	}

	public void restore(int currentPlayingIndex, int currentPlayingPosition, boolean autoPlay) {

		if (currentPlayingIndex != -1)
		{
			MediaPlayer.getPlayerService().waitForPlayer();

			play(currentPlayingIndex, autoPlayStart);

			// TODO: This logic should be inside the various player services
			MediaPlayer.getPlayerService().restore(currentPlaying, currentPlayingIndex, currentPlayingPosition, autoPlay);
			autoPlayStart = false;
		}
	}

	public synchronized void play(int index)
	{
		play(index, true);
	}

	private synchronized void play(int index, boolean start)
	{
		updateRemoteControl();

		if (index < 0 || index >= playQueue.size())
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
			// current service
			PlayerService lastPlayerService = MediaPlayer.getPlayerService();
			setCurrentPlaying(index);
			PlayerService nextPlayerService = MediaPlayer.getPlayerService();
			if (lastPlayerService != nextPlayerService) {
				lastPlayerService.reset();
			}

			MediaPlayer.getPlayerService().play(getCurrentPlayingIndex());
			DownloadServiceImpl.getInstance().checkDownloads();
			setNextPlaying();
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

	public synchronized void seekTo(int position)
	{
		MediaPlayer.getPlayerService().seek(getCurrentPlayingIndex(), position);
		updateRemoteControl();
	}

	public synchronized void previous()
	{
		int index = getCurrentPlayingIndex();
		if (index == -1)
		{
			return;
		}

		// Restart song if played more than five seconds.
		stop();
		if (getPlayerPosition() > 5000 || index == 0)
		{
			play(index);
		}
		else
		{
			play(index - 1);
		}
	}

	public synchronized void next()
	{
		int index = getCurrentPlayingIndex();
		if (index != -1)
		{
			stop();
			play(index + 1);
		}
	}

	public void onSongCompleted()
	{
		int index = getCurrentPlayingIndex();

		if (currentPlaying != null)
		{
			final MusicDirectory.Entry song = currentPlaying.getSong();

			if (song != null && song.getBookmarkPosition() > 0 && Util.getShouldClearBookmark(this))
			{
				MusicService musicService = MusicServiceFactory.getMusicService(MediaPlayer.this);
				try
				{
					musicService.deleteBookmark(song.getId(), this, null);
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
					if (index + 1 < 0 || index + 1 >= playQueue.size())
					{
						if (Util.getShouldClearPlaylist(this))
						{
							((DownloadServiceImpl)DownloadServiceImpl.getInstance()).clear(true);
						}

						resetPlayback();
						break;
					}

					play(index + 1);
					break;
				case ALL:
					play((index + 1) % playQueue.size());
					break;
				case SINGLE:
					play(index);
					break;
				default:
					break;
			}
		}
	}

	public synchronized void pause()
	{
		try
		{
			if (playerState == STARTED)
			{
				MediaPlayer.getPlayerService().pause();
				setPlayerState(PAUSED);
			}
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	public synchronized void stop()
	{
		try
		{
			if (playerState == STARTED)
			{
				MediaPlayer.getPlayerService().stop();
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

	public synchronized void start()
	{
		try
		{
			MediaPlayer.getPlayerService().start();
			setPlayerState(STARTED);
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	public synchronized void reset()
	{
		MediaPlayer.getPlayerService().reset();
		setPlayerState(IDLE);
	}

	public synchronized int getPlayerPosition()
	{
		try
		{
			if (playerState == IDLE || playerState == DOWNLOADING || playerState == PREPARING)
			{
				return 0;
			}

			return MediaPlayer.getPlayerService().getPosition();
		}
		catch (Exception x)
		{
			handleError(x);
			return 0;
		}
	}

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
				return MediaPlayer.getPlayerService().getDuration();
			}
			catch (Exception x)
			{
				handleError(x);
			}
		}
		return 0;
	}

	public PlayerState getPlayerState()
	{
		return playerState;
	}

	public PlayerState getNextPlayerState()
	{
		return nextPlayerState;
	}

	synchronized void setPlayerState(PlayerState playerState)
	{
		Log.i(TAG, String.format("%s -> %s (%s)", this.playerState.name(), playerState.name(), currentPlaying));

		this.playerState = playerState;

		if (this.playerState == PAUSED)
		{
			DownloadServiceImpl.getInstance().serializeDownloadQueue();
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

		MediaPlayer.getPlayerService().notifyPlayerStateChange(playerState);
	}

	public void setPlayerStateCompleted()
	{
		Log.i(TAG, String.format("%s -> %s (%s)", this.playerState.name(), PlayerState.COMPLETED, currentPlaying));
		this.playerState = PlayerState.COMPLETED;
		scrobbler.scrobble(this, currentPlaying, true);
		MediaPlayer.getPlayerService().notifyPlayerStateChange(playerState);
	}

	protected synchronized void setNextPlayerState(PlayerState playerState)
	{
		Log.i(TAG, String.format("Next: %s -> %s (%s)", this.nextPlayerState.name(), playerState.name(), nextPlaying));
		this.nextPlayerState = playerState;
	}

	public boolean getEqualizerAvailable()
	{
		return MediaPlayer.getPlayerService().getEqualizerAvailable();
	}

	public boolean getVisualizerAvailable()
	{
		return MediaPlayer.getPlayerService().getVisualizerAvailable();
	}


	public EqualizerController getEqualizerController()
	{
		return MediaPlayer.getPlayerService().getEqualizerController();
	}

	public VisualizerController getVisualizerController()
	{
		return MediaPlayer.getPlayerService().getVisualizerController();
	}

	public boolean isJukeboxEnabled()
	{
		return jukeboxEnabled;
	}

	public boolean isJukeboxAvailable()
	{
		MusicService musicService = MusicServiceFactory.getMusicService(this);

		try
		{
			String username = Util.getUserName(this, Util.getActiveServer(this));
			UserInfo user = musicService.getUser(username, this, null);
			return user.getJukeboxRole();
		}
		catch (Exception e)
		{
			Log.w("Error getting user information", e);
		}

		return false;
	}

	/**
	 * Plays or resumes the playback, depending on the current player state.
	 */
	public synchronized void togglePlayPause()
	{
		if (playerState == PAUSED || playerState == COMPLETED || playerState == STOPPED)
		{
			start();
		}
		else if (playerState == IDLE)
		{
			autoPlayStart = true;
			play();
		}
		else if (playerState == STARTED)
		{
			pause();
		}
	}

	public synchronized int getCurrentPlayingIndex()
	{
		return playQueue.indexOf(currentPlaying);
	}

	public DownloadFile getCurrentPlaying()
	{
		return currentPlaying;
	}

	synchronized void setCurrentPlaying(int currentPlayingIndex)
	{
		try
		{
			setCurrentPlaying(playQueue.get(currentPlayingIndex));
		}
		catch (IndexOutOfBoundsException x)
		{
			// Ignored
		}
	}

	synchronized void setCurrentPlaying(DownloadFile currentPlaying)
	{
		this.currentPlaying = currentPlaying;

		if (currentPlaying != null)
		{
			Util.broadcastNewTrackInfo(this, currentPlaying.getSong());
			Util.broadcastA2dpMetaDataChange(this);
		}
		else
		{
			Util.broadcastNewTrackInfo(this, null);
			Util.broadcastA2dpMetaDataChange(this);
		}

		updateRemoteControl();

		// Update widget
		UltraSonicAppWidgetProvider4x1.getInstance().notifyChange(this, this, playerState == PlayerState.STARTED, false);
		UltraSonicAppWidgetProvider4x2.getInstance().notifyChange(this, this, playerState == PlayerState.STARTED, true);
		UltraSonicAppWidgetProvider4x3.getInstance().notifyChange(this, this, playerState == PlayerState.STARTED, false);
		UltraSonicAppWidgetProvider4x4.getInstance().notifyChange(this, this, playerState == PlayerState.STARTED, false);
		SubsonicTabActivity tabInstance = SubsonicTabActivity.getInstance();

		if (currentPlaying != null)
		{
			if (tabInstance != null)
			{
				int size = Util.getNotificationImageSize(this);

				tabInstance.nowPlayingImage = FileUtil.getAlbumArtBitmap(this, currentPlaying.getSong(), size, true);
				tabInstance.showNotification(handler, currentPlaying.getSong(), this, this.notification, this.playerState);
				tabInstance.showNowPlaying();
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
	}

	synchronized DownloadFile getNextPlaying() {
		return nextPlaying;
	}

	synchronized void setNextPlaying()
	{
		boolean gaplessPlayback = Util.getGaplessPlaybackPreference(MediaPlayer.this);

		if (!gaplessPlayback)
		{
			nextPlaying = null;
			nextPlayerState = IDLE;
			return;
		}

		int index = getCurrentPlayingIndex();

		if (index != -1)
		{
			switch (getRepeatMode())
			{
				case OFF:
					index += 1;
					break;
				case ALL:
					index = (index + 1) % playQueue.size();
					break;
				case SINGLE:
					break;
				default:
					break;
			}
		}

		nextSetup = false;
		if (nextPlayingTask != null)
		{
			nextPlayingTask.cancel();
			nextPlayingTask = null;
		}

		if (index < playQueue.size() && index != -1)
		{
			nextPlaying = playQueue.get(index);
			nextPlayingTask = new CheckCompletionTask(nextPlaying);
			nextPlayingTask.start();
		}
		else
		{
			nextPlaying = null;
			setNextPlayerState(IDLE);
		}
	}

	private synchronized void resetPlayback()
	{
		reset();
		setCurrentPlaying(null);
		DownloadServiceImpl.getInstance().serializeDownloadQueue();
	}

	public IBinder onBind(Intent intent)
	{
		return binder;
	}




	public void setUpRemoteControlClient()
	{
		if (!Util.isLockScreenEnabled(this)) return;

		ComponentName componentName = new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName());

		if (remoteControlClient == null)
		{
			final Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
			mediaButtonIntent.setComponent(componentName);
			PendingIntent broadcast = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			remoteControlClient = new RemoteControlClient(broadcast);
			audioManager.registerRemoteControlClient(remoteControlClient);

			// Flags for the media transport control that this client supports.
			int flags = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS |
					RemoteControlClient.FLAG_KEY_MEDIA_NEXT |
					RemoteControlClient.FLAG_KEY_MEDIA_PLAY |
					RemoteControlClient.FLAG_KEY_MEDIA_PAUSE |
					RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE |
					RemoteControlClient.FLAG_KEY_MEDIA_STOP;

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
			{
				flags |= RemoteControlClient.FLAG_KEY_MEDIA_POSITION_UPDATE;

				remoteControlClient.setOnGetPlaybackPositionListener(new RemoteControlClient.OnGetPlaybackPositionListener()
				{
					@Override
					public long onGetPlaybackPosition()
					{
						return MediaPlayer.getInstance().getPlayerPosition();
					}
				});

				remoteControlClient.setPlaybackPositionUpdateListener(new RemoteControlClient.OnPlaybackPositionUpdateListener()
				{
					@Override
					public void onPlaybackPositionUpdate(long newPositionMs)
					{
						seekTo((int) newPositionMs);
					}
				});
			}

			remoteControlClient.setTransportControlFlags(flags);
		}
	}

	private void clearRemoteControl()
	{
		if (remoteControlClient != null)
		{
			remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
			audioManager.unregisterRemoteControlClient(remoteControlClient);
			remoteControlClient = null;
		}
	}

	private void updateRemoteControl()
	{
		if (!Util.isLockScreenEnabled(this))
		{
			clearRemoteControl();
			return;
		}

		if (remoteControlClient != null)
		{
			audioManager.unregisterRemoteControlClient(remoteControlClient);
			audioManager.registerRemoteControlClient(remoteControlClient);
		}
		else
		{
			setUpRemoteControlClient();
		}

		Log.i(TAG, String.format("In updateRemoteControl, playerState: %s [%d]", playerState, getPlayerPosition()));

		switch (playerState)
		{
			case STARTED:
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
				{
					remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
				}
				else
				{
					remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING, getPlayerPosition(), 1.0f);
				}
				break;
			default:
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
				{
					remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
				}
				else
				{
					remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED, getPlayerPosition(), 1.0f);
				}
				break;
		}

		if (currentPlaying != null)
		{
			MusicDirectory.Entry currentSong = currentPlaying.getSong();

			Bitmap lockScreenBitmap = FileUtil.getAlbumArtBitmap(this, currentSong, Util.getMinDisplayMetric(this), true);

			String artist = currentSong.getArtist();
			String album = currentSong.getAlbum();
			String title = currentSong.getTitle();
			Integer currentSongDuration = currentSong.getDuration();
			Long duration = 0L;

			if (currentSongDuration != null) duration = (long) currentSongDuration * 1000;

			remoteControlClient.editMetadata(true).putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artist).putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, artist).putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, album).putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title).putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration)
					.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, lockScreenBitmap).apply();
		}
	}

	public float getVolume()
	{
		return MediaPlayer.getPlayerService().getVolume();
	}


	public void setVolume(float volume)
	{
		MediaPlayer.getPlayerService().setVolume(volume);
	}

	public void setJukeboxEnabled(boolean jukeboxEnabled)
	{
		if (jukeboxEnabled != this.jukeboxEnabled)
		{
			this.jukeboxEnabled = jukeboxEnabled;
			this.clear();
			MediaPlayer.getPlayerService().startJukeboxService(jukeboxEnabled);
		}
	}

	public void updatePlaylist() {
		MediaPlayer.getPlayerService().syncPlaylist();
	}

	public RepeatMode getRepeatMode()
	{
		return Util.getRepeatMode(this);
	}

	public void setRepeatMode(RepeatMode repeatMode)
	{
		Util.setRepeatMode(this, repeatMode);
		setNextPlaying();
	}

	public boolean getKeepScreenOn()
	{
		return keepScreenOn;
	}

	public void setKeepScreenOn(boolean keepScreenOn)
	{
		this.keepScreenOn = keepScreenOn;
	}

	public boolean getShowVisualization()
	{
		return showVisualization;
	}

	public void setShowVisualization(boolean showVisualization)
	{
		this.showVisualization = showVisualization;
	}

	protected synchronized void setupNext(final DownloadFile downloadFile) {
		MediaPlayer.getPlayerService().setupNext(downloadFile);
	}

	public void postMediaTask(Runnable r) {
		mediaPlayerHandler.post(r);
	}



	private class CheckCompletionTask extends CancellableTask
	{
		private final DownloadFile downloadFile;
		private final File partialFile;

		public CheckCompletionTask(DownloadFile downloadFile)
		{
			super();
			MediaPlayer.getInstance().setNextPlayerState(PlayerState.IDLE);

			this.downloadFile = downloadFile;

			partialFile = downloadFile != null ? downloadFile.getPartialFile() : null;
		}

		@Override
		public void execute()
		{
			Thread.currentThread().setName("CheckCompletionTask");

			if (downloadFile == null)
			{
				return;
			}

			// Do an initial sleep so this prepare can't compete with main prepare
			Util.sleepQuietly(5000L);

			while (!bufferComplete())
			{
				Util.sleepQuietly(5000L);

				if (isCancelled())
				{
					return;
				}
			}

			// Start the setup of the next media player
			MediaPlayer.getInstance().postMediaTask(new Runnable()
			{
				@Override
				public void run()
				{
					MediaPlayer.getInstance().setupNext(downloadFile);
				}
			});
		}

		private boolean bufferComplete()
		{
			boolean completeFileAvailable = downloadFile.isWorkDone();
			Log.i(TAG, String.format("Buffering next %s (%d)", partialFile, partialFile.length()));
			return completeFileAvailable && (MediaPlayer.getInstance().getPlayerState() == PlayerState.STARTED || MediaPlayer.getInstance().getPlayerState() == PlayerState.PAUSED);
		}

		@Override
		public String toString()
		{
			return String.format("CheckCompletionTask (%s)", downloadFile);
		}
	}

	protected void handleError(Exception x)
	{
		Log.w(TAG, String.format("Media player error: %s", x), x);

		try
		{
			reset();
		}
		catch (Exception ex)
		{
			Log.w(TAG, String.format("Exception encountered when resetting media player: %s", ex), ex);
		}

		setPlayerState(IDLE);
	}

	public synchronized void enqueue(List<MusicDirectory.Entry> songs, boolean save, boolean autoplay, boolean playNext, boolean shuffle, boolean newPlaylist)
	{
		shufflePlay = false;
		int offset = 1;

		if (songs.isEmpty())
		{
			return;
		}

		if (newPlaylist)
		{
			clear();
		}

		if (playNext)
		{
			if (autoplay && getCurrentPlayingIndex() >= 0)
			{
				offset = 0;
			}

			for (MusicDirectory.Entry song : songs)
			{
				DownloadFile downloadFile = new DownloadFile(this, song, save);
				addSong(getCurrentPlayingIndex() + offset, downloadFile);
				offset++;
			}
		}
		else
		{
			int size = playQueue.size();
			int index = getCurrentPlayingIndex();

			for (MusicDirectory.Entry song : songs)
			{
				DownloadFile downloadFile = new DownloadFile(this, song, save);
				addSong(-1, downloadFile);
			}

			if (!autoplay && (size - 1) == index)
			{
				setNextPlaying();
			}
		}
		updatePlaylist();

		if (shuffle) shuffle();

		if (autoplay)
		{
			play(0);
		}
	}

	protected void addSong(int offset, DownloadFile downloadFile) {
		if (offset < 0) {
			playQueue.add(downloadFile);
			return;
		}
		playQueue.add(offset, downloadFile);
	}

	// this should be called when service is done adding files
	protected void playlistUpdated() {
		DownloadServiceImpl.getInstance().serializeDownloadQueue();
	}

	public synchronized void enqueue(MusicDirectory musicDirectory) {

		clear();
		for (MusicDirectory.Entry entry : musicDirectory.getChildren()) {
			playQueue.add(new DownloadFile(this, entry, false));
		}
		DownloadServiceImpl.getInstance().serializeDownloadQueue();
		MediaPlayer.getInstance().updatePlaylist();
		MediaPlayer.getInstance().setNextPlaying();
	}

	public void restore(List<MusicDirectory.Entry> songs, int currentPlayingIndex, int currentPlayingPosition, boolean autoPlay, boolean newPlaylist)
	{
		enqueue(songs, false, false, false, false, newPlaylist);
	}


	public synchronized void shuffle()
	{
		Collections.shuffle(playQueue);
		DownloadFile currentPlaying = MediaPlayer.getInstance().getCurrentPlaying();
		if (currentPlaying != null)
		{
			playQueue.remove(MediaPlayer.getInstance().getCurrentPlayingIndex());
			playQueue.add(0, currentPlaying);
		}
		DownloadServiceImpl.getInstance().serializeDownloadQueue();
		MediaPlayer.getInstance().updatePlaylist();
		MediaPlayer.getInstance().setNextPlaying();
	}

	public synchronized void setShufflePlayEnabled(boolean enabled)
	{
		shufflePlay = enabled;
		if (shufflePlay)
		{
			clear();
		}
	}

	public boolean isShufflePlayEnabled()
	{
		return shufflePlay;
	}

	public synchronized void clear() {

		reset();
		incrementPlaylistRevision();
		setCurrentPlaying(null);
		playQueue.clear();
		((DownloadServiceImpl)DownloadServiceImpl.getInstance()).clear(true);
	}

	public List<DownloadFile> getPlayQueue() {
		return new ArrayList<DownloadFile>(playQueue);
	}

	public synchronized void remove(DownloadFile downloadFile) {
		playQueue.remove(downloadFile);
	}

	public synchronized void remove(int which)
	{
		playQueue.remove(which);
	}

	public int size() {
		return playQueue.size();
	}

	public long getPlaylistDuration()
	{
		long totalDuration = 0;

		for (DownloadFile downloadFile : playQueue)
		{
			MusicDirectory.Entry entry = downloadFile.getSong();

			if (!entry.isDirectory())
			{
				if (entry.getArtist() != null)
				{
					Integer duration = entry.getDuration();

					if (duration != null)
					{
						totalDuration += duration;
					}
				}
			}
		}

		return totalDuration;
	}

	protected synchronized void checkShufflePlay()
	{
		// Get users desired random playlist size
		int listSize = Util.getMaxSongs(this);
		boolean wasEmpty = playQueue.isEmpty();

		long revisionBefore = revision;

		// First, ensure that list is at least 20 songs long.
		int size = size();
		if (size < listSize)
		{
			for (MusicDirectory.Entry song : shufflePlayBuffer.get(listSize - size))
			{
				DownloadFile downloadFile = new DownloadFile(this, song, false);
				// FIXME!!!
				playQueue.add(downloadFile);
				incrementPlaylistRevision();
			}
		}

		DownloadFile currentPlaying = MediaPlayer.getInstance().getCurrentPlaying();
		int currIndex = currentPlaying == null ? 0 : MediaPlayer.getInstance().getCurrentPlayingIndex();

		// Only shift playlist if playing song #5 or later.
		if (currIndex > 4)
		{
			int songsToShift = currIndex - 2;
			for (MusicDirectory.Entry song : shufflePlayBuffer.get(songsToShift))
			{
				playQueue.add(new DownloadFile(this, song, false));
				playQueue.get(0).cancelDownload();
				playQueue.remove(0);
				incrementPlaylistRevision();
			}
		}

		if (revisionBefore != revision)
		{
			MediaPlayer.getInstance().updatePlaylist();
		}

		if (wasEmpty && !playQueue.isEmpty())
		{
			MediaPlayer.getInstance().play(0);
		}
	}

	public void incrementPlaylistRevision() {
		revision++;
	}

	public long getPlaylistRevision() {
		return revision;
	}

	protected void setPlayQueue(MusicDirectory musicDirectory, boolean save) {
		playQueue.clear();
		for (MusicDirectory.Entry song : musicDirectory.getChildren())
		{
			DownloadFile downloadFile = new DownloadFile(this, song, save);
			playQueue.add(downloadFile);
		}
		incrementPlaylistRevision();
		updateRemoteControl();
	}

	public Notification getNotification() {
		return this.notification;
	}

}
