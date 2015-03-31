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

 Copyright 2010 (C) Sindre Mehus
 */
package com.thejoshwa.ultrasonic.androidapp.view;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.SectionIndexer;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.Genre;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Sindre Mehus
 */
public class GenreAdapter extends ArrayAdapter<Genre> implements SectionIndexer
{

	// Both arrays are indexed by section ID.
	private final Object[] sections;
	private final Integer[] positions;

	public GenreAdapter(Context context, List<Genre> genres)
	{
		super(context, R.layout.artist_list_item, genres);

		Collection<String> sectionSet = new LinkedHashSet<String>(30);
		List<Integer> positionList = new ArrayList<Integer>(30);

		for (int i = 0; i < genres.size(); i++)
		{
			Genre genre = genres.get(i);
			String index = genre.getIndex();
			if (!sectionSet.contains(index))
			{
				sectionSet.add(index);
				positionList.add(i);
			}
		}

		sections = sectionSet.toArray(new Object[sectionSet.size()]);
		positions = positionList.toArray(new Integer[positionList.size()]);
	}

	@Override
	public Object[] getSections()
	{
		return sections;
	}

	@Override
	public int getPositionForSection(int section)
	{
		return positions[section];
	}

	@Override
	public int getSectionForPosition(int pos)
	{
		for (int i = 0; i < sections.length - 1; i++)
		{
			if (pos < positions[i + 1])
			{
				return i;
			}
		}
		return sections.length - 1;
	}
}
