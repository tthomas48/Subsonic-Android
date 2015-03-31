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

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.thejoshwa.ultrasonic.androidapp.domain.Artist;
import com.thejoshwa.ultrasonic.androidapp.domain.Genre;
import com.thejoshwa.ultrasonic.androidapp.domain.Indexes;
import com.thejoshwa.ultrasonic.androidapp.domain.JukeboxStatus;
import com.thejoshwa.ultrasonic.androidapp.domain.Lyrics;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicFolder;
import com.thejoshwa.ultrasonic.androidapp.domain.Playlist;
import com.thejoshwa.ultrasonic.androidapp.domain.SearchCriteria;
import com.thejoshwa.ultrasonic.androidapp.domain.SearchResult;
import com.thejoshwa.ultrasonic.androidapp.domain.Share;
import com.thejoshwa.ultrasonic.androidapp.domain.UserInfo;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.FileUtil;
import com.thejoshwa.ultrasonic.androidapp.util.ProgressListener;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author Sindre Mehus
 */
public class OfflineMusicService extends RESTMusicService
{
	private static final String TAG = OfflineMusicService.class.getSimpleName();
	private static final Pattern COMPILE = Pattern.compile(" ");

	@Override
	public boolean isLicenseValid(Context context, ProgressListener progressListener) throws Exception
	{
		return true;
	}

	@Override
	public Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		List<Artist> artists = new ArrayList<Artist>();
		File root = FileUtil.getMusicDirectory(context);
		for (File file : FileUtil.listFiles(root))
		{
			if (file.isDirectory())
			{
				Artist artist = new Artist();
				artist.setId(file.getPath());
				artist.setIndex(file.getName().substring(0, 1));
				artist.setName(file.getName());
				artists.add(artist);
			}
		}

		String ignoredArticlesString = "The El La Los Las Le Les";
		final String[] ignoredArticles = COMPILE.split(ignoredArticlesString);

		Collections.sort(artists, new Comparator<Artist>()
		{
			@Override
			public int compare(Artist lhsArtist, Artist rhsArtist)
			{
				String lhs = lhsArtist.getName().toLowerCase();
				String rhs = rhsArtist.getName().toLowerCase();

				char lhs1 = lhs.charAt(0);
				char rhs1 = rhs.charAt(0);

				if (Character.isDigit(lhs1) && !Character.isDigit(rhs1))
				{
					return 1;
				}

				if (Character.isDigit(rhs1) && !Character.isDigit(lhs1))
				{
					return -1;
				}

				for (String article : ignoredArticles)
				{
					int index = lhs.indexOf(String.format("%s ", article.toLowerCase()));

					if (index == 0)
					{
						lhs = lhs.substring(article.length() + 1);
					}

					index = rhs.indexOf(String.format("%s ", article.toLowerCase()));

					if (index == 0)
					{
						rhs = rhs.substring(article.length() + 1);
					}
				}

				return lhs.compareTo(rhs);
			}
		});

		return new Indexes(0L, ignoredArticlesString, Collections.<Artist>emptyList(), artists);
	}

	@Override
	public MusicDirectory getMusicDirectory(String id, String artistName, boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		File dir = new File(id);
		MusicDirectory result = new MusicDirectory();
		result.setName(dir.getName());

		Collection<String> names = new HashSet<String>();

		for (File file : FileUtil.listMediaFiles(dir))
		{
			String name = getName(file);
			if (name != null & !names.contains(name))
			{
				names.add(name);
				result.addChild(createEntry(context, file, name));
			}
		}

		return result;
	}

	private static String getName(File file)
	{
		String name = file.getName();

		if (file.isDirectory())
		{
			return name;
		}

		if (name.endsWith(".partial") || name.contains(".partial.") || name.equals(Constants.ALBUM_ART_FILE))
		{
			return null;
		}

		name = name.replace(".complete", "");
		return FileUtil.getBaseName(name);
	}

	private static MusicDirectory.Entry createEntry(Context context, File file, String name)
	{
		MusicDirectory.Entry entry = new MusicDirectory.Entry();
		entry.setIsDirectory(file.isDirectory());
		entry.setId(file.getPath());
		entry.setParent(file.getParent());
		entry.setSize(file.length());
		String root = FileUtil.getMusicDirectory(context).getPath();
		entry.setPath(file.getPath().replaceFirst(String.format("^%s/", root), ""));
		entry.setTitle(name);

		if (file.isFile())
		{
			String artist = null;
			String album = null;
			String title = null;
			String track = null;
			String disc = null;
			String year = null;
			String genre = null;
			String duration = null;
			String hasVideo = null;

			try
			{
				MediaMetadataRetriever mmr = new MediaMetadataRetriever();
				mmr.setDataSource(file.getPath());
				artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
				album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
				title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
				track = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
				disc = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER);
				year = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
				genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
				duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
				hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
				mmr.release();
			}
			catch (Exception ignored)
			{
			}

			entry.setArtist(artist != null ? artist : file.getParentFile().getParentFile().getName());
			entry.setAlbum(album != null ? album : file.getParentFile().getName());

			if (title != null)
			{
				entry.setTitle(title);
			}

			entry.setIsVideo(hasVideo != null);

			Log.i("OfflineMusicService", String.format("Offline Stuff: %s", track));

			if (track != null)
			{

				int trackValue = 0;

				try
				{
					int slashIndex = track.indexOf('/');

					if (slashIndex > 0)
					{
						track = track.substring(0, slashIndex);
					}

					trackValue = Integer.parseInt(track);
				}
				catch (Exception ex)
				{
					Log.e("OfflineMusicService", String.format("Offline Stuff: %s", ex));
				}

				Log.i("OfflineMusicService", String.format("Offline Stuff: Setting Track: %d", trackValue));

				entry.setTrack(trackValue);
			}

			if (disc != null)
			{
				int discValue = 0;

				try
				{
					int slashIndex = disc.indexOf('/');

					if (slashIndex > 0)
					{
						disc = disc.substring(0, slashIndex);
					}

					discValue = Integer.parseInt(disc);
				}
				catch (Exception ignored)
				{
				}

				entry.setDiscNumber(discValue);
			}

			if (year != null)
			{
				int yearValue = 0;

				try
				{
					yearValue = Integer.parseInt(year);
				}
				catch (Exception ignored)
				{
				}

				entry.setYear(yearValue);
			}

			if (genre != null)
			{
				entry.setGenre(genre);
			}

			if (duration != null)
			{
				long durationValue = 0;

				try
				{
					durationValue = Long.parseLong(duration);
					durationValue = TimeUnit.MILLISECONDS.toSeconds(durationValue);
				}
				catch (Exception ignored)
				{
				}

				entry.setDuration(durationValue);
			}
		}

		entry.setSuffix(FileUtil.getExtension(file.getName().replace(".complete", "")));

		File albumArt = FileUtil.getAlbumArtFile(context, entry);

		if (albumArt.exists())
		{
			entry.setCoverArt(albumArt.getPath());
		}

		return entry;
	}

	@Override
	public Bitmap getAvatar(Context context, String username, int size, boolean saveToFile, boolean highQuality, ProgressListener progressListener) throws Exception
	{
		try
		{
			Bitmap bitmap = FileUtil.getAvatarBitmap(username, size, highQuality);
			return Util.scaleBitmap(bitmap, size);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	@Override
	public Bitmap getCoverArt(Context context, MusicDirectory.Entry entry, int size, boolean saveToFile, boolean highQuality, ProgressListener progressListener) throws Exception
	{
		try
		{
			Bitmap bitmap = FileUtil.getAlbumArtBitmap(context, entry, size, highQuality);
			return Util.scaleBitmap(bitmap, size);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	@Override
	public void star(String id, String albumId, String artistId, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Star not available in offline mode");
	}

	@Override
	public void unstar(String id, String albumId, String artistId, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("UnStar not available in offline mode");
	}

	@Override
	public List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Music folders not available in offline mode");
	}

	@Override
	public SearchResult search(SearchCriteria criteria, Context context, ProgressListener progressListener) throws Exception
	{
		List<Artist> artists = new ArrayList<Artist>();
		List<MusicDirectory.Entry> albums = new ArrayList<MusicDirectory.Entry>();
		List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>();
		File root = FileUtil.getMusicDirectory(context);
		int closeness;

		for (File artistFile : FileUtil.listFiles(root))
		{
			String artistName = artistFile.getName();
			if (artistFile.isDirectory())
			{
				if ((closeness = matchCriteria(criteria, artistName)) > 0)
				{
					Artist artist = new Artist();
					artist.setId(artistFile.getPath());
					artist.setIndex(artistFile.getName().substring(0, 1));
					artist.setName(artistName);
					artist.setCloseness(closeness);
					artists.add(artist);
				}

				recursiveAlbumSearch(artistName, artistFile, criteria, context, albums, songs);
			}
		}

		Collections.sort(artists, new Comparator<Artist>()
		{
			@Override
			public int compare(Artist lhs, Artist rhs)
			{
				if (lhs.getCloseness() == rhs.getCloseness())
				{
					return 0;
				}

				else return lhs.getCloseness() > rhs.getCloseness() ? -1 : 1;
			}
		});
		Collections.sort(albums, new Comparator<MusicDirectory.Entry>()
		{
			@Override
			public int compare(MusicDirectory.Entry lhs, MusicDirectory.Entry rhs)
			{
				if (lhs.getCloseness() == rhs.getCloseness())
				{
					return 0;
				}

				else return lhs.getCloseness() > rhs.getCloseness() ? -1 : 1;
			}
		});
		Collections.sort(songs, new Comparator<MusicDirectory.Entry>()
		{
			@Override
			public int compare(MusicDirectory.Entry lhs, MusicDirectory.Entry rhs)
			{
				if (lhs.getCloseness() == rhs.getCloseness())
				{
					return 0;
				}

				else return lhs.getCloseness() > rhs.getCloseness() ? -1 : 1;
			}
		});

		return new SearchResult(artists, albums, songs);
	}

	private static void recursiveAlbumSearch(String artistName, File file, SearchCriteria criteria, Context context, List<MusicDirectory.Entry> albums, List<MusicDirectory.Entry> songs)
	{
		int closeness;

		for (File albumFile : FileUtil.listMediaFiles(file))
		{
			if (albumFile.isDirectory())
			{
				String albumName = getName(albumFile);
				if ((closeness = matchCriteria(criteria, albumName)) > 0)
				{
					MusicDirectory.Entry album = createEntry(context, albumFile, albumName);
					album.setArtist(artistName);
					album.setCloseness(closeness);
					albums.add(album);
				}

				for (File songFile : FileUtil.listMediaFiles(albumFile))
				{
					String songName = getName(songFile);

					if (songFile.isDirectory())
					{
						recursiveAlbumSearch(artistName, songFile, criteria, context, albums, songs);
					}
					else if ((closeness = matchCriteria(criteria, songName)) > 0)
					{
						MusicDirectory.Entry song = createEntry(context, albumFile, songName);
						song.setArtist(artistName);
						song.setAlbum(albumName);
						song.setCloseness(closeness);
						songs.add(song);
					}
				}
			}
			else
			{
				String songName = getName(albumFile);

				if ((closeness = matchCriteria(criteria, songName)) > 0)
				{
					MusicDirectory.Entry song = createEntry(context, albumFile, songName);
					song.setArtist(artistName);
					song.setAlbum(songName);
					song.setCloseness(closeness);
					songs.add(song);
				}
			}
		}
	}

	private static int matchCriteria(SearchCriteria criteria, String name)
	{
		String query = criteria.getQuery().toLowerCase();
		String[] queryParts = COMPILE.split(query);
		String[] nameParts = COMPILE.split(name.toLowerCase());

		int closeness = 0;

		for (String queryPart : queryParts)
		{
			for (String namePart : nameParts)
			{
				if (namePart.equals(queryPart))
				{
					closeness++;
				}
			}
		}

		return closeness;
	}

	@Override
	public List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		List<Playlist> playlists = new ArrayList<Playlist>();
		File root = FileUtil.getPlaylistDirectory();
		String lastServer = null;
		boolean removeServer = true;
		for (File folder : FileUtil.listFiles(root))
		{
			if (folder.isDirectory())
			{
				String server = folder.getName();
				SortedSet<File> fileList = FileUtil.listFiles(folder);
				for (File file : fileList)
				{
					if (FileUtil.isPlaylistFile(file))
					{
						String id = file.getName();
						String filename = server + ": " + FileUtil.getBaseName(id);
						Playlist playlist = new Playlist(server, filename);
						playlists.add(playlist);
					}
				}

				if (!server.equals(lastServer) && !fileList.isEmpty())
				{
					if (lastServer != null)
					{
						removeServer = false;
					}
					lastServer = server;
				}
			}
			else
			{
				// Delete legacy playlist files
				try
				{
					folder.delete();
				}
				catch (Exception e)
				{
					Log.w(TAG, String.format("Failed to delete old playlist file: %s", folder.getName()));
				}
			}
		}

		if (removeServer)
		{
			for (Playlist playlist : playlists)
			{
				playlist.setName(playlist.getName().substring(playlist.getId().length() + 2));
			}
		}
		return playlists;
	}

	@Override
	public MusicDirectory getPlaylist(String id, String name, Context context, ProgressListener progressListener) throws Exception
	{
		DownloadService downloadService = DownloadServiceImpl.getInstance();
		if (downloadService == null)
		{
			return new MusicDirectory();
		}

		Reader reader = null;
		BufferedReader buffer = null;
		try
		{
			int firstIndex = name.indexOf(id);

			if (firstIndex != -1)
			{
				name = name.substring(id.length() + 2);
			}

			File playlistFile = FileUtil.getPlaylistFile(id, name);
			reader = new FileReader(playlistFile);
			buffer = new BufferedReader(reader);

			MusicDirectory playlist = new MusicDirectory();
			String line = buffer.readLine();
			if (!"#EXTM3U".equals(line)) return playlist;

			while ((line = buffer.readLine()) != null)
			{
				File entryFile = new File(line);
				String entryName = getName(entryFile);

				if (entryFile.exists() && entryName != null)
				{
					playlist.addChild(createEntry(context, entryFile, entryName));
				}
			}

			return playlist;
		}
		finally
		{
			Util.close(buffer);
			Util.close(reader);
		}
	}

	@Override
	public void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context, ProgressListener progressListener) throws Exception
	{
		File playlistFile = FileUtil.getPlaylistFile(Util.getServerName(context), name);
		FileWriter fw = new FileWriter(playlistFile);
		BufferedWriter bw = new BufferedWriter(fw);
		try
		{
			fw.write("#EXTM3U\n");
			for (MusicDirectory.Entry e : entries)
			{
				String filePath = FileUtil.getSongFile(context, e).getAbsolutePath();
				if (!new File(filePath).exists())
				{
					String ext = FileUtil.getExtension(filePath);
					String base = FileUtil.getBaseName(filePath);
					filePath = base + ".complete." + ext;
				}
				fw.write(filePath + '\n');
			}
		}
		catch (Exception e)
		{
			Log.w(TAG, "Failed to save playlist: " + name);
		}
		finally
		{
			bw.close();
			fw.close();
		}
	}

	@Override
	public void deletePlaylist(String id, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Playlists not available in offline mode");
	}

	@Override
	public void updatePlaylist(String id, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Updating playlist not available in offline mode");
	}

	@Override
	public void removeFromPlaylist(String id, List<Integer> toRemove, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Removing from playlist not available in offline mode");
	}

	@Override
	public void updatePlaylist(String id, String name, String comment, boolean pub, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Updating playlist not available in offline mode");
	}

	@Override
	public Lyrics getLyrics(String artist, String title, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Lyrics not available in offline mode");
	}

	@Override
	public void scrobble(String id, boolean submission, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Scrobbling not available in offline mode");
	}

	@Override
	public MusicDirectory getAlbumList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Album lists not available in offline mode");
	}

	@Override
	public String getVideoUrl(Context context, String id, boolean useFlash)
	{
		return null;
	}

	@Override
	public String getVideoStreamUrl(int maxBitrate, Context context, String id)
	{
		return null;
	}

	@Override
	public JukeboxStatus updateJukeboxPlaylist(List<String> ids, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Jukebox not available in offline mode");
	}

	@Override
	public JukeboxStatus skipJukebox(int index, int offsetSeconds, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Jukebox not available in offline mode");
	}

	@Override
	public JukeboxStatus stopJukebox(Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Jukebox not available in offline mode");
	}

	@Override
	public JukeboxStatus startJukebox(Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Jukebox not available in offline mode");
	}

	@Override
	public JukeboxStatus getJukeboxStatus(Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Jukebox not available in offline mode");
	}

	@Override
	public JukeboxStatus setJukeboxGain(float gain, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Jukebox not available in offline mode");
	}

	@Override
	public SearchResult getStarred(Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Starred not available in offline mode");
	}

	@Override
	public MusicDirectory getRandomSongs(int size, Context context, ProgressListener progressListener) throws Exception
	{
		File root = FileUtil.getMusicDirectory(context);
		List<File> children = new LinkedList<File>();
		listFilesRecursively(root, children);
		MusicDirectory result = new MusicDirectory();

		if (children.isEmpty())
		{
			return result;
		}

		Random random = new java.security.SecureRandom();
		for (int i = 0; i < size; i++)
		{
			File file = children.get(random.nextInt(children.size()));
			result.addChild(createEntry(context, file, getName(file)));
		}

		return result;
	}

	@Override
	public MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Getting Songs By Genre not available in offline mode");
	}

	@Override
	public List<Genre> getGenres(Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Getting Genres not available in offline mode");
	}

	@Override
	public UserInfo getUser(String username, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Getting user info not available in offline mode");
	}

	@Override
	public List<Share> createShare(List<String> ids, String description, Long expires, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Creating shares not available in offline mode");
	}

	@Override
	public List<Share> getShares(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Getting shares not available in offline mode");
	}

	@Override
	public void deleteShare(String id, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Deleting shares not available in offline mode");
	}

	@Override
	public void updateShare(String id, String description, Long expires, Context context, ProgressListener progressListener) throws Exception
	{
		throw new OfflineException("Updating shares not available in offline mode");
	}

	private static void listFilesRecursively(File parent, List<File> children)
	{
		for (File file : FileUtil.listMediaFiles(parent))
		{
			if (file.isFile())
			{
				children.add(file);
			}
			else
			{
				listFilesRecursively(file, children);
			}
		}
	}
}
