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
package com.thejoshwa.ultrasonic.androidapp.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous loading of images, with caching.
 * <p/>
 * There should normally be only one instance of this class.
 *
 * @author Sindre Mehus
 */
public class ImageLoader implements Runnable
{
	private static final String TAG = ImageLoader.class.getSimpleName();

	private final LRUCache<String, Bitmap> cache = new LRUCache<String, Bitmap>(150);
	private final BlockingQueue<Task> queue;
	private int imageSizeDefault;
	private final int imageSizeLarge;
	private Bitmap largeUnknownImage;
	private Bitmap unknownAvatarImage;
	private Context context;
	private Collection<Thread> threads;
	private AtomicBoolean running = new AtomicBoolean();
	private int concurrency;

	public ImageLoader(Context context, int concurrency)
	{
		this.context = context;
		this.concurrency = concurrency;
		queue = new LinkedBlockingQueue<Task>(1000);

		Resources resources = context.getResources();
		Drawable drawable = resources.getDrawable(R.drawable.unknown_album);

		// Determine the density-dependent image sizes.
		if (drawable != null)
		{
			imageSizeDefault = drawable.getIntrinsicHeight();
		}

		imageSizeLarge = Util.getMaxDisplayMetric(context);
		createLargeUnknownImage(context);
		createUnknownAvatarImage(context);
	}

	public synchronized boolean isRunning()
	{
		return running.get() && !threads.isEmpty();
	}

	public void setConcurrency(int concurrency)
	{
		this.concurrency = concurrency;
	}

	public void startImageLoader()
	{
		running.set(true);

		threads = Collections.synchronizedCollection(new ArrayList<Thread>(this.concurrency));

		for (int i = 0; i < this.concurrency; i++)
		{
			Thread thread = new Thread(this, String.format("ImageLoader_%d", i));
			threads.add(thread);
			thread.start();
		}
	}

	public synchronized void stopImageLoader()
	{
		clear();

		for (Thread thread : threads)
		{
			thread.interrupt();
		}

		running.set(false);
		threads.clear();
	}

	private void createLargeUnknownImage(Context context)
	{
		BitmapDrawable drawable = (BitmapDrawable) context.getResources().getDrawable(R.drawable.unknown_album_large);
		Log.i(TAG, "createLargeUnknownImage");

		if (drawable != null)
		{
			largeUnknownImage = Util.scaleBitmap(drawable.getBitmap(), imageSizeLarge);
		}
	}

	private void createUnknownAvatarImage(Context context)
	{
		Resources res = context.getResources();
		Drawable contact = res.getDrawable(R.drawable.ic_contact_picture);
		unknownAvatarImage = Util.createBitmapFromDrawable(contact);
	}

	public void loadAvatarImage(ImageHolder imageHolder, String username, boolean large, int size, boolean crossFade, boolean highQuality)
	{
		imageHolder.invalidate();

		if (username == null)
		{
			setUnknownAvatarImage(imageHolder);
			return;
		}

		if (size <= 0)
		{
			size = large ? imageSizeLarge : imageSizeDefault;
		}

		Bitmap bitmap = cache.get(getKey(username, size));

		if (bitmap != null)
		{
			setAvatarImageBitmap(imageHolder, username, bitmap, crossFade);
			return;
		}

		setUnknownAvatarImage(imageHolder);

		queue.offer(new Task(imageHolder, username, size, large, crossFade, highQuality));
	}

	public void loadImage(ImageHolder holder, MusicDirectory.Entry entry, boolean large, int size, boolean crossFade, boolean highQuality)
	{
		holder.invalidate();

		if (entry == null)
		{
			setUnknownImage(holder, large);
			return;
		}

		String coverArt = entry.getCoverArt();

		if (coverArt == null)
		{
			setUnknownImage(holder, large);
			return;
		}

		if (size <= 0)
		{
			size = large ? imageSizeLarge : imageSizeDefault;
		}

		Bitmap bitmap = cache.get(getKey(coverArt, size));

		if (bitmap != null)
		{
			setImageBitmap(holder, entry, bitmap, crossFade);
			return;
		}

		setUnknownImage(holder, large);

		queue.offer(new Task(holder, entry, size, large, crossFade, highQuality));
		return;
	}

	private static String getKey(String coverArtId, int size)
	{
		return String.format("%s:%d", coverArtId, size);
	}

	public Bitmap getImageBitmap(String username, int size)
	{
		Bitmap bitmap = cache.get(getKey(username, size));

		if (bitmap != null && !bitmap.isRecycled())
		{
			Bitmap.Config config = bitmap.getConfig();
			return bitmap.copy(config, false);
		}

		return null;
	}

	public Bitmap getImageBitmap(MusicDirectory.Entry entry, boolean large, int size)
	{
		if (entry == null)
		{
			return null;
		}

		String coverArt = entry.getCoverArt();

		if (coverArt == null)
		{
			return null;
		}

		if (size <= 0)
		{
			size = large ? imageSizeLarge : imageSizeDefault;
		}

		Bitmap bitmap = cache.get(getKey(coverArt, size));

		if (bitmap != null && !bitmap.isRecycled())
		{
			Bitmap.Config config = bitmap.getConfig();
			return bitmap.copy(config, false);
		}

		return null;
	}

	private void setImageBitmap(ImageHolder imageHolder, MusicDirectory.Entry entry, Bitmap bitmap, boolean crossFade)
	{
		MusicDirectory.Entry tagEntry = (MusicDirectory.Entry) imageHolder.getTag();

		// Only apply image to the view if the view is intended for this entry
		if (entry != null && tagEntry != null && !entry.equals(tagEntry))
		{
			Log.i(TAG, "View is no longer valid, not setting ImageBitmap");
			return;
		}

		if (crossFade)
		{
			imageHolder.crossFade(bitmap);
		} else
		{
			imageHolder.updateImage(bitmap);
		}
	}

	private void setAvatarImageBitmap(ImageHolder imageHolder, String username, Bitmap bitmap, boolean crossFade)
	{
		String tagEntry = (String) imageHolder.getTag();

		// Only apply image to the view if the view is intended for this entry
		if (username != null && tagEntry != null && !username.equals(tagEntry))
		{
			Log.i(TAG, "View is no longer valid, not setting ImageBitmap");
			return;
		}

		if (crossFade)
		{
			imageHolder.crossFade(bitmap);
		} else
		{
			imageHolder.updateImage(bitmap);
		}
	}

	public void setUnknownAvatarImage(ImageHolder imageHolder)
	{
		setAvatarImageBitmap(imageHolder, null, unknownAvatarImage, false);
	}

	public void setUnknownImage(ImageHolder holder, boolean large)
	{
		if (large)
		{
			setImageBitmap(holder, null, largeUnknownImage, false);
		} else
		{
			holder.updateImageResource(R.drawable.unknown_album);
		}
	}

	public void addImageToCache(Bitmap bitmap, MusicDirectory.Entry entry, int size)
	{
		cache.put(getKey(entry.getCoverArt(), size), bitmap);
	}

	public void addImageToCache(Bitmap bitmap, String username, int size)
	{
		cache.put(getKey(username, size), bitmap);
	}

	public void clear()
	{
		queue.clear();
	}

	@Override
	public void run()
	{
		while (running.get())
		{
			try
			{
				Task task = queue.take();
				task.execute();
			} catch (InterruptedException ignored)
			{
				running.set(false);
				break;
			} catch (Throwable x)
			{
				Log.e(TAG, "Unexpected exception in ImageLoader.", x);
			}
		}
	}

	private class Task
	{
		private final ImageHolder imageHolder;
		private final MusicDirectory.Entry entry;
		private final String username;
		private final Handler handler;
		private final int size;
		private final boolean saveToFile;
		private final boolean crossFade;
		private final boolean highQuality;

		public Task(ImageHolder imageHolder, String username, int size, boolean saveToFile, boolean crossFade, boolean highQuality)
		{
			this.imageHolder = imageHolder;
			this.entry = null;
			this.username = username;
			this.size = size;
			this.saveToFile = saveToFile;
			this.crossFade = crossFade;
			this.highQuality = highQuality;
			handler = new Handler();
		}

		public Task(ImageHolder imageHolder, MusicDirectory.Entry entry, int size, boolean saveToFile, boolean crossFade, boolean highQuality)
		{
			this.imageHolder = imageHolder;
			this.entry = entry;
			this.username = null;
			this.size = size;
			this.saveToFile = saveToFile;
			this.crossFade = crossFade;
			this.highQuality = highQuality;
			handler = new Handler();
		}

		public void execute()
		{
			try
			{
				Context context = imageHolder.getContext();
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				final boolean isAvatar = this.username != null && this.entry == null;
				final Bitmap bitmap = this.entry != null ? musicService.getCoverArt(context, entry, size, saveToFile, highQuality, null) : musicService.getAvatar(context, username, size, saveToFile, highQuality, null);

				if (isAvatar)
				{
					addImageToCache(bitmap, username, size);
					handler.post(new Runnable()
					{
						@Override
						public void run()
						{
							setAvatarImageBitmap(imageHolder, username, bitmap, crossFade);
						}
					});
					return;
				}
				handler.post(new Runnable()
				{
					@Override
					public void run()
					{


						addImageToCache(bitmap, entry, size);
						setImageBitmap(imageHolder, entry, bitmap, crossFade);
					}

				});
			} catch (Throwable x)
			{
				Log.e(TAG, "Failed to download art.", x);
			}
		}
	}
}
