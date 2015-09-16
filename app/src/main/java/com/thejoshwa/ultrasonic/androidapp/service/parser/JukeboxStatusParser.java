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
import android.util.Log;

import com.thejoshwa.ultrasonic.androidapp.domain.JukeboxStatus;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;

import org.xmlpull.v1.XmlPullParser;

import java.io.Reader;

/**
 * @author Sindre Mehus
 */
public class JukeboxStatusParser extends MusicDirectoryEntryParser
{

	public JukeboxStatusParser(Context context)
	{
		super(context);
	}

	public JukeboxStatus parse(Reader reader) throws Exception
	{

		init(reader);

		JukeboxStatus jukeboxStatus = new JukeboxStatus();
		MusicDirectory directory = new MusicDirectory();
		int eventType;
		do
		{
			eventType = nextParseEvent();
			if (eventType == XmlPullParser.START_TAG)
			{
				String name = getElementName();
				if ("jukeboxPlaylist".equals(name) || "jukeboxStatus".equals(name))
				{
					jukeboxStatus.setPositionSeconds(getInteger("position"));
					jukeboxStatus.setCurrentIndex(getInteger("currentIndex"));
					jukeboxStatus.setPlaying(getBoolean("playing"));
					jukeboxStatus.setGain(getFloat("gain"));
				}
				else if ("entry".equals(name)) {
					directory.addChild(parseEntry("", false, 0));
				}
				else if ("error".equals(name))
				{
					handleError();
				}
			}
		} while (eventType != XmlPullParser.END_DOCUMENT);

		Log.e("JUKEBOXSTATUSPARSER", "Found this many files " + directory.getChildren());
		jukeboxStatus.setPlaylist(directory);

		validate();

		return jukeboxStatus;
	}
}