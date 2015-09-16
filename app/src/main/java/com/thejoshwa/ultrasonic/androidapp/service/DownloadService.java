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

import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory.Entry;

import java.util.List;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public interface DownloadService
{

	//void download(List<Entry> songs, boolean save, boolean autoplay, boolean playNext, boolean shuffle, boolean newPlaylist);

	void downloadBackground(List<Entry> songs, boolean save);

	void clearBackground();

	void clearIncomplete();

	void remove(DownloadFile downloadFile);

	//public List<DownloadFile> getSongs();

	//List<DownloadFile> getDownloads();

	List<DownloadFile> getBackgroundDownloads();

	DownloadFile getCurrentDownloading();

	void delete(List<Entry> songs);

	void unpin(List<Entry> songs);

	DownloadFile forSong(Entry song);

	public void setSuggestedPlaylistName(String name);

	public String getSuggestedPlaylistName();

	public boolean isSharingAvailable();

	public void checkDownloads();

	public void serializeDownloadQueue();

	public List<DownloadFile> getDownloads();
}
