/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package com.thejoshwa.ultrasonic.androidapp.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.UserInfo;
import com.thejoshwa.ultrasonic.androidapp.util.LRUCache;
import com.thejoshwa.ultrasonic.androidapp.util.SimpleServiceBinder;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.DOWNLOADING;

/**
 * @author Sindre Mehus, Joshua Bahnsen
 * @version $Id$
 */
public class DownloadServiceImpl extends Service implements DownloadService
{
	private static final String TAG = DownloadServiceImpl.class.getSimpleName();

	private final IBinder binder = new SimpleServiceBinder<DownloadService>(this);
	private final List<DownloadFile> backgroundDownloadList = new ArrayList<DownloadFile>();
	private final DownloadServiceLifecycleSupport lifecycleSupport = new DownloadServiceLifecycleSupport(this);

	private final LRUCache<MusicDirectory.Entry, DownloadFile> downloadFileCache = new LRUCache<MusicDirectory.Entry, DownloadFile>(100);
	private final List<DownloadFile> cleanupCandidates = new ArrayList<DownloadFile>();

	private DownloadFile currentDownloading;
	private static DownloadService instance;
	private String suggestedPlaylistName;


	private final static int lockScreenBitmapSize = 500;


	@Override
	public void onCreate()
	{
		super.onCreate();

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Thread.currentThread().setName("DownloadServiceImpl");

				Looper.prepare();
				Looper.loop();
			}
		}).start();

		instance = this;
		lifecycleSupport.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		super.onStartCommand(intent, flags, startId);
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
		}
		catch (Throwable ignored)
		{
		}
	}

	public static DownloadService getInstance()
	{
		return instance;
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return binder;
	}

	@Override
	public synchronized void downloadBackground(List<MusicDirectory.Entry> songs, boolean save)
	{
		for (MusicDirectory.Entry song : songs)
		{
			DownloadFile downloadFile = new DownloadFile(this, song, save);
			backgroundDownloadList.add(downloadFile);
		}

		checkDownloads();
		lifecycleSupport.serializeDownloadQueue();
	}

	@Override
	public synchronized DownloadFile forSong(MusicDirectory.Entry song)
	{
		for (DownloadFile downloadFile : MediaPlayer.getInstance().getPlayQueue())
		{
			if (downloadFile.getSong().equals(song) && ((downloadFile.isDownloading() && !downloadFile.isDownloadCancelled() && downloadFile.getPartialFile().exists()) || downloadFile.isWorkDone()))
			{
				return downloadFile;
			}
		}
		for (DownloadFile downloadFile : backgroundDownloadList)
		{
			if (downloadFile.getSong().equals(song))
			{
				return downloadFile;
			}
		}

		DownloadFile downloadFile = downloadFileCache.get(song);
		if (downloadFile == null)
		{
			downloadFile = new DownloadFile(this, song, false);
			downloadFileCache.put(song, downloadFile);
		}
		return downloadFile;
	}

	@Override
	public synchronized void clearBackground()
	{
		if (currentDownloading != null && backgroundDownloadList.contains(currentDownloading))
		{
			currentDownloading.cancelDownload();
			currentDownloading = null;
		}
		backgroundDownloadList.clear();
	}

	@Override
	public synchronized void clearIncomplete()
	{
		MediaPlayer.getInstance().reset();
		Iterator<DownloadFile> iterator = MediaPlayer.getInstance().getPlayQueue().iterator();

		while (iterator.hasNext())
		{
			DownloadFile downloadFile = iterator.next();
			if (!downloadFile.isCompleteFileAvailable())
			{
				iterator.remove();
			}
		}

		lifecycleSupport.serializeDownloadQueue();
		MediaPlayer.getInstance().updatePlaylist();
	}

	protected synchronized void clear(boolean serialize)
	{
		if (currentDownloading != null)
		{
			currentDownloading.cancelDownload();
			currentDownloading = null;
		}

		if (serialize)
		{
			lifecycleSupport.serializeDownloadQueue();
		}
	}


	@Override
	public synchronized void remove(DownloadFile downloadFile)
	{
		if (downloadFile == currentDownloading)
		{
			currentDownloading.cancelDownload();
			currentDownloading = null;
		}
		if (downloadFile == MediaPlayer.getInstance().getCurrentPlaying())
		{
			MediaPlayer.getInstance().reset();
			MediaPlayer.getInstance().setCurrentPlaying(null);
		}
		MediaPlayer.getInstance().remove(downloadFile);
		backgroundDownloadList.remove(downloadFile);
		lifecycleSupport.serializeDownloadQueue();
		MediaPlayer.getInstance().updatePlaylist();
		if (downloadFile == MediaPlayer.getInstance().getCurrentPlaying())
		{
			MediaPlayer.getInstance().setNextPlaying();
		}
	}

	@Override
	public synchronized void delete(List<MusicDirectory.Entry> songs)
	{
		for (MusicDirectory.Entry song : songs)
		{
			forSong(song).delete();
		}
	}

	@Override
	public synchronized void unpin(List<MusicDirectory.Entry> songs)
	{
		for (MusicDirectory.Entry song : songs)
		{
			forSong(song).unpin();
		}
	}



	@Override
	public DownloadFile getCurrentDownloading()
	{
		return currentDownloading;
	}


	@Override
	public synchronized List<DownloadFile> getDownloads()
	{
		List<DownloadFile> temp = new ArrayList<DownloadFile>();
		temp.addAll(MediaPlayer.getInstance().getPlayQueue());
		temp.addAll(backgroundDownloadList);
		return temp;
	}

	@Override
	public List<DownloadFile> getBackgroundDownloads()
	{
		return backgroundDownloadList;
	}







	@Override
	public void setSuggestedPlaylistName(String name)
	{
		this.suggestedPlaylistName = name;
	}

	@Override
	public String getSuggestedPlaylistName()
	{
		return suggestedPlaylistName;
	}


	@Override
	public boolean isSharingAvailable()
	{
		MusicService musicService = MusicServiceFactory.getMusicService(DownloadServiceImpl.this);

		try
		{
			String username = Util.getUserName(DownloadServiceImpl.this, Util.getActiveServer(DownloadServiceImpl.this));
			UserInfo user = musicService.getUser(username, DownloadServiceImpl.this, null);
			return user.getShareRole();
		}
		catch (Exception e)
		{
			Log.w("Error getting user information", e);
		}

		return false;
	}







	@Override
	public synchronized void checkDownloads()
	{
		if (!Util.isExternalStoragePresent() || !lifecycleSupport.isExternalStorageAvailable())
		{
			return;
		}

		if (MediaPlayer.getInstance().isShufflePlayEnabled())
		{
			MediaPlayer.getInstance().checkShufflePlay();
		}

		if (MediaPlayer.getInstance().isJukeboxEnabled() || !Util.isNetworkConnected(this))
		{
			return;
		}

		if (MediaPlayer.getInstance().getPlayQueue().isEmpty() && backgroundDownloadList.isEmpty())
		{
			return;
		}

		// Need to download current playing?
		DownloadFile currentPlaying = MediaPlayer.getInstance().getCurrentPlaying();
		if (currentPlaying != null && currentPlaying != currentDownloading && !currentPlaying.isWorkDone())
		{
			// Cancel current download, if necessary.
			if (currentDownloading != null)
			{
				currentDownloading.cancelDownload();
			}

			currentDownloading = currentPlaying;
			currentDownloading.download();
			cleanupCandidates.add(currentDownloading);
		}

		// Find a suitable target for download.
		else
		{
			if (currentDownloading == null || currentDownloading.isWorkDone() || currentDownloading.isFailed() && (!MediaPlayer.getInstance().getPlayQueue().isEmpty() || !backgroundDownloadList.isEmpty()))
			{
				currentDownloading = null;
				int n = MediaPlayer.getInstance().size();

				int preloaded = 0;

				if (n != 0)
				{
					int start = currentPlaying == null ? 0 : MediaPlayer.getInstance().getCurrentPlayingIndex();
					if (start == -1)
					{
						start = 0;
					}
					int i = start;
					do
					{
						DownloadFile downloadFile = MediaPlayer.getInstance().getPlayQueue().get(i);
						if (!downloadFile.isWorkDone())
						{
							if (downloadFile.shouldSave() || preloaded < Util.getPreloadCount(this))
							{
								currentDownloading = downloadFile;
								currentDownloading.download();
								cleanupCandidates.add(currentDownloading);
								if (i == (start + 1))
								{
									MediaPlayer.getInstance().setNextPlayerState(DOWNLOADING);
								}
								break;
							}
						}
						else if (currentPlaying != downloadFile)
						{
							preloaded++;
						}

						i = (i + 1) % n;
					} while (i != start);
				}

				if ((preloaded + 1 == n || preloaded >= Util.getPreloadCount(this) || MediaPlayer.getInstance().getPlayQueue().isEmpty()) && !backgroundDownloadList.isEmpty())
				{
					for (int i = 0; i < backgroundDownloadList.size(); i++)
					{
						DownloadFile downloadFile = backgroundDownloadList.get(i);
						PlayerService playerService = MediaPlayer.getPlayerService(downloadFile);
						if (downloadFile.isWorkDone() && (!downloadFile.shouldSave() || downloadFile.isSaved()))
						{
							if (Util.getShouldScanMedia(this))
							{
								Util.scanMedia(this, downloadFile.getCompleteFile());
							}

							// Don't need to keep list like active song list
							backgroundDownloadList.remove(i);
							MediaPlayer.getInstance().incrementPlaylistRevision();
							i--;
						}
						else
						{
							currentDownloading = downloadFile;
							currentDownloading.download();
							cleanupCandidates.add(currentDownloading);
							break;
						}
					}
				}
			}
		}

		// Delete obsolete .partial and .complete files.
		cleanup();
	}


	private synchronized void cleanup()
	{
		DownloadFile currentPlaying = MediaPlayer.getInstance().getCurrentPlaying();

		Iterator<DownloadFile> iterator = cleanupCandidates.iterator();
		while (iterator.hasNext())
		{
			DownloadFile downloadFile = iterator.next();
			if (downloadFile != currentPlaying && downloadFile != currentDownloading)
			{
				if (downloadFile.cleanup())
				{
					iterator.remove();
				}
			}
		}
	}

	@Override
	public void serializeDownloadQueue() {
		lifecycleSupport.serializeDownloadQueue();
	}




}