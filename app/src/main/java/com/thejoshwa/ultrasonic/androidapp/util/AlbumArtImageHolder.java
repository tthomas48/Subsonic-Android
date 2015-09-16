package com.thejoshwa.ultrasonic.androidapp.util;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.os.Build;
import android.support.v7.graphics.Palette;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.activity.SubsonicTabActivity;

/**
 * Created by tthomas on 7/3/15.
 */
public class AlbumArtImageHolder extends ImageHolder
{
	private SeekBar progressBar;
	private NotificationManager notificationManager;
	public AlbumArtImageHolder(NotificationManager notificationManager, Activity activity) {
		super();
		this.notificationManager = notificationManager;
		progressBar = (SeekBar) activity.findViewById(R.id.download_progress_bar);

		addBackground(activity.findViewById(R.id.download_back));
		addBackground(activity.findViewById(R.id.media_buttons));
		addImageView((ImageView) activity.findViewById(R.id.download_album_art_image));

		int[] mediaButtons = new int[] {
				R.id.download_shuffle,
				R.id.download_previous,
				R.id.download_start,
				R.id.download_pause,
				R.id.download_stop,
				R.id.download_next,
				R.id.download_repeat
		};
		for (int id : mediaButtons) {
			addTitleText((TextView) activity.findViewById(id));
		}
		addTitleText((TextView) activity.findViewById(R.id.download_track));
		addTitleText((TextView) activity.findViewById(R.id.download_song_title));
		addBodyText((TextView) activity.findViewById(R.id.download_album));
		addBodyText((TextView) activity.findViewById(R.id.download_position));
		addBodyText((TextView) activity.findViewById(R.id.download_total_duration));
		addBodyText((TextView) activity.findViewById(R.id.download_duration));
	}

	protected void updatePalette(Palette.Swatch swatch) {
		super.updatePalette(swatch);
		setProgressBarColor(swatch.getTitleTextColor());
	}

	private void setProgressBarColor(int newColor){
		if (progressBar == null) {
			return;
		}
		LayerDrawable ld = (LayerDrawable) progressBar.getProgressDrawable();
		ScaleDrawable d1 = (ScaleDrawable) ld.findDrawableByLayerId(android.R.id.progress);
		d1.setColorFilter(newColor, PorterDuff.Mode.SRC_IN);
	}

	@Override
	protected void afterUpdate(Bitmap bitmap, final Palette.Swatch vibrant) {

		super.afterUpdate(bitmap, vibrant);
		if (bitmap == null) {
			return;
		}

		Notification notification = SubsonicTabActivity.getInstance().getMediaPlayer().getNotification();
		if (notification == null) {
			return;
		}
		RemoteViews notificationView = notification.contentView;
		RemoteViews bigNotificationView = null;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
		{
			bigNotificationView = notification.bigContentView;
		}
		if (vibrant != null)
		{
			notificationView.setInt(R.id.statusbar, "setBackgroundColor", vibrant.getRgb());
			notificationView.setTextColor(R.id.trackname, vibrant.getTitleTextColor());
			notificationView.setTextColor(R.id.artist, vibrant.getBodyTextColor());
			notificationView.setTextColor(R.id.album, vibrant.getBodyTextColor());
		}

		notificationView.setImageViewBitmap(R.id.notification_image, bitmap);


		if (bigNotificationView != null)
		{
			bigNotificationView.setImageViewBitmap(R.id.notification_image, bitmap);
			bigNotificationView.setInt(R.id.statusbar, "setBackgroundColor", vibrant.getRgb());
			bigNotificationView.setTextColor(R.id.trackname, vibrant.getTitleTextColor());
			bigNotificationView.setTextColor(R.id.artist, vibrant.getBodyTextColor());
			bigNotificationView.setTextColor(R.id.album, vibrant.getBodyTextColor());
		}
		this.notificationManager.cancel(Constants.NOTIFICATION_ID_PLAYING);
		this.notificationManager.notify(Constants.NOTIFICATION_ID_PLAYING, notification);

	}


}
