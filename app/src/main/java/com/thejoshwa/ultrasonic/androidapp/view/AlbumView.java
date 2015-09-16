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
package com.thejoshwa.ultrasonic.androidapp.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.util.ImageHolder;
import com.thejoshwa.ultrasonic.androidapp.util.ImageLoader;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

/**
 * Used to display albums in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class AlbumView extends UpdateView
{
	private static final String TAG = AlbumView.class.getSimpleName();
	private static Drawable starDrawable;
	private static Drawable starHollowDrawable;
	private static String theme;

	private Context context;
	private MusicDirectory.Entry entry;
	private EntryAdapter.AlbumViewHolder viewHolder;
	private ImageHolder imageHolder;
	private ImageLoader imageLoader;

	public AlbumView(Context context, ImageLoader imageLoader)
	{
		super(context);
		this.context = context;
		this.imageLoader = imageLoader;

		String theme = Util.getTheme(context);
		boolean themesMatch = theme.equals(AlbumView.theme);
		AlbumView.theme = theme;

		if (starHollowDrawable == null || !themesMatch)
		{
			starHollowDrawable = Util.getDrawableFromAttribute(context, R.attr.star_hollow);
		}

		if (starDrawable == null || !themesMatch)
		{
			starDrawable = Util.getDrawableFromAttribute(context, R.attr.star_full);
		}
	}

	public void setLayout()
	{
		LayoutInflater.from(context).inflate(R.layout.album_list_item, this, true);
		viewHolder = new EntryAdapter.AlbumViewHolder();
		viewHolder.background = findViewById(R.id.album_background);
		viewHolder.title = (TextView) findViewById(R.id.album_title);
		viewHolder.artist = (TextView) findViewById(R.id.album_artist);
		viewHolder.cover_art = (ImageView) findViewById(R.id.album_coverart);
		viewHolder.star = (ImageView) findViewById(R.id.album_star);
		setTag(viewHolder);

		imageHolder = new ImageHolder();
		imageHolder.addImageView(viewHolder.cover_art);
		imageHolder.addBackground(viewHolder.background);
		imageHolder.addTitleText(viewHolder.title);
		imageHolder.addBodyText(viewHolder.artist);
	}

	public void setViewHolder(EntryAdapter.AlbumViewHolder viewHolder)
	{
		this.viewHolder = viewHolder;
		this.viewHolder.cover_art.invalidate();
		setTag(this.viewHolder);
	}

	public MusicDirectory.Entry getEntry()
	{
		return this.entry;
	}

	public void setAlbum(final MusicDirectory.Entry album)
	{
		viewHolder.cover_art.setTag(album);
		imageLoader.loadImage(imageHolder, album, false, 0, false, true);
		this.entry = album;


		String title = album.getTitle();
		String artist = album.getArtist();
		boolean starred = album.getStarred();

		viewHolder.title.setText(title);
		viewHolder.artist.setText(artist);
		viewHolder.artist.setVisibility(artist == null ? View.GONE : View.VISIBLE);
		viewHolder.star.setImageDrawable(starred ? starDrawable : starHollowDrawable);

		if (Util.isOffline(this.context) || "-1".equals(album.getId()))
		{
			viewHolder.star.setVisibility(View.GONE);
		}
		else
		{
			viewHolder.star.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View view)
				{
					final boolean isStarred = album.getStarred();
					final String id = album.getId();

					if (!isStarred)
					{
						viewHolder.star.setImageDrawable(starDrawable);
						album.setStarred(true);
					}
					else
					{
						viewHolder.star.setImageDrawable(starHollowDrawable);
						album.setStarred(false);
					}

					new Thread(new Runnable()
					{
						@Override
						public void run()
						{
							MusicService musicService = MusicServiceFactory.getMusicService(null);
							boolean useId3 = Util.getShouldUseId3Tags(getContext());

							try
							{
								if (!isStarred)
								{
									musicService.star(!useId3 ? id : null, useId3 ? id : null, null, getContext(), null);
								}
								else
								{
									musicService.unstar(!useId3 ? id : null, useId3 ? id : null, null, getContext(), null);
								}
							}
							catch (Exception e)
							{
								Log.e(TAG, e.getMessage(), e);
							}
						}
					}).start();
				}
			});
		}
	}
}