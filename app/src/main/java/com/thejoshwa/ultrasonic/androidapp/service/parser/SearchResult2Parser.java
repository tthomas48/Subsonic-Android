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
package com.thejoshwa.ultrasonic.androidapp.service.parser;

import android.content.Context;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.SearchResult;
import com.thejoshwa.ultrasonic.androidapp.domain.Artist;
import com.thejoshwa.ultrasonic.androidapp.util.ProgressListener;

import org.xmlpull.v1.XmlPullParser;

import java.io.Reader;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Sindre Mehus
 */
public class SearchResult2Parser extends MusicDirectoryEntryParser
{

	public SearchResult2Parser(Context context)
	{
		super(context);
	}

	public SearchResult parse(Reader reader, ProgressListener progressListener, boolean useId3) throws Exception
	{
		updateProgress(progressListener, R.string.parser_reading);
		init(reader);

		List<Artist> artists = new ArrayList<Artist>();
		List<MusicDirectory.Entry> albums = new ArrayList<MusicDirectory.Entry>();
		List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>();
		int eventType;
		do
		{
			eventType = nextParseEvent();
			if (eventType == XmlPullParser.START_TAG)
			{
				String name = getElementName();
				if ("artist".equals(name))
				{
					Artist artist = new Artist();
					artist.setId(get("id"));
					artist.setName(get("name"));
					artists.add(artist);
				}
				else if ("album".equals(name))
				{
					albums.add(parseEntry("", useId3, 0));
				}
				else if ("song".equals(name))
				{
					songs.add(parseEntry("", false, 0));
				}
				else if ("error".equals(name))
				{
					handleError();
				}
			}
		} while (eventType != XmlPullParser.END_DOCUMENT);

		validate();
		updateProgress(progressListener, R.string.parser_reading_done);

		return new SearchResult(artists, albums, songs);
	}

}