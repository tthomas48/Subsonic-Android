package com.thejoshwa.ultrasonic.androidapp.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.support.v7.graphics.Palette;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tthomas on 7/3/15.
 */
public class ImageHolder
{
	private List<ImageView> images = new ArrayList<ImageView>();
	private List<View> backgrounds = new ArrayList<View>();
	private List<TextView> titles = new ArrayList<TextView>();
	private List<TextView> bodies = new ArrayList<TextView>();

	public ImageHolder() {

	}

	public void addImageView(ImageView view) {
		images.add(view);
	}

	public void addBackground(View view) {
		backgrounds.add(view);
	}

	public void addTitleText(TextView view) {
		titles.add(view);
	}

	public void addBodyText(TextView view) {
		bodies.add(view);
	}

	public void invalidate()
	{

		for (ImageView imageView : images)
		{
			imageView.invalidate();
		}
	}

	public void crossFade(Bitmap bitmap) {
		for (ImageView view : images)
		{
			Drawable existingDrawable = view.getDrawable();
			Drawable newDrawable = Util.createDrawableFromBitmap(view.getContext(), bitmap);

			if (existingDrawable == null)
			{
				Bitmap emptyImage = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
				existingDrawable = new BitmapDrawable(view.getContext().getResources(), emptyImage);
			}

			Drawable[] layers = new Drawable[]{existingDrawable, newDrawable};

			TransitionDrawable transitionDrawable = new TransitionDrawable(layers);
			view.setImageDrawable(transitionDrawable);
			transitionDrawable.startTransition(250);
			updatePalette(bitmap);
		}

	}

	public void updateImageResource(int resource) {
		if (images.size() == 0) {
			return;
		}

		for (ImageView imageView : images) {
			imageView.setImageResource(resource);
		}

		Bitmap bitmap = ((BitmapDrawable) images.get(0).getDrawable()).getBitmap();
		updatePalette(bitmap);
	}

	/*
	public void updateImageDrawable(Drawable drawable) {

		if (images.size() == 0) {
			return;
		}

		for (ImageView imageView : images) {
			imageView.setImageDrawable(drawable);
		}

		Bitmap bitmap = ((BitmapDrawable) images.get(0).getDrawable()).getBitmap();
		updatePalette(bitmap);
	}
	*/

	public void updateImage(Bitmap bitmap)
	{

		if (bitmap == null)
		{
			return;
		}

		for (ImageView imageView : images)
		{
			imageView.setImageBitmap(bitmap);
		}
		updatePalette(bitmap);
	}

	public Context getContext() {

		if (images.size() == 0) {
			return null;
		}

		return images.get(0).getContext();
	}

	public Object getTag() {

		if (images.size() == 0) {
			return null;
		}

		return  images.get(0).getTag();
	}


	private void updatePalette(Bitmap bitmap)
	{


		Palette.Swatch swatch = getSwatch(bitmap);
		if (swatch != null)
		{
			updatePalette(swatch);
		}
		afterUpdate(bitmap, swatch);
	}

	protected void updatePalette(Palette.Swatch swatch) {
		for (View background : backgrounds)
		{
			background.setBackgroundColor(swatch.getRgb());
		}
		for (TextView titleText : titles)
		{
			titleText.setTextColor(swatch.getTitleTextColor());
		}

		for (TextView bodyText : bodies)
		{
			bodyText.setTextColor(swatch.getBodyTextColor());
		}
	}

	protected void afterUpdate(Bitmap bitmap, final Palette.Swatch vibrant) {

	}


	private Palette.Swatch getSwatch(Bitmap image) {

		if (image == null) {
			return null;
		}
		Palette palette = Palette.generate(image);
		Palette.Swatch vibrantSwatch = palette.getVibrantSwatch();
		if (vibrantSwatch != null) {
			return vibrantSwatch;
		}
		List<Palette.Swatch> swatches = palette.getSwatches();
		if (swatches.size() == 0) {
			return null;
		}
		return swatches.get(0);
	}

}
