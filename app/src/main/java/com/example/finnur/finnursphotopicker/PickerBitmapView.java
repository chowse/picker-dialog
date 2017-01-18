// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// FLIP
//package org.chromium.chrome.browser;
package com.example.finnur.finnursphotopicker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.TextView;

// Chrome-specific:
/*
FLIP
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.selection.SelectableItemView;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
*/

import java.util.List;

public class PickerBitmapView extends SelectableItemView<PickerBitmap> {
    private Context mContext;

    // Our parent category.
    private PickerCategoryView mCategoryView;

    // The image view we are showing.
    private ImageView mIconView;
    private View mScrim;
    private int mOriginalSize;

    // Our selection delegate.
    private SelectionDelegate<PickerBitmap> mSelectionDelegate;

    // The request we are showing the bitmap for.
    private PickerBitmap mItem;

    // The control that signifies the image has been selected.
    public ImageView mSelectedView;

    // The control that signifies the image has not been selected.
    public ImageView mUnselectedView;

    // The camera/gallery icon.
    public ImageView mSpecialTileView;

    // The label under the special tile.
    public TextView mSpecialTileLabel;

    // Whether the image has been loaded already.
    public boolean mImageLoaded;

    // The amount to use for the border.
    private int mBorder;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (mCategoryView == null) return;  // Android studio calls onMeasure to draw the widget.
        int width = mCategoryView.getImageSize();
        int height = mCategoryView.getImageSize();
        setMeasuredDimension(width, height);
    }

    private static void addPaddingToParent(View view, int padding) {
        ViewGroup layout = (ViewGroup) view.getParent();
        layout.setPadding(padding, padding, padding, padding);
        layout.requestLayout();
    }

    private class ResizeWidthAnimation extends Animation {
        private View mView;
        private View mScrim;

        private int mStartingSize;
        private int mTargetSize;

        public ResizeWidthAnimation(View view, View scrim, int size) {
            mView = view;
            mScrim = scrim;
            mStartingSize = view.getWidth();
            mTargetSize = size;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation transformation) {
            int newSize =
                    mStartingSize + (int) ((mTargetSize - mStartingSize) * interpolatedTime);
            int padding = (Math.max(mStartingSize, mTargetSize) - newSize) / 2;

            mView.getLayoutParams().height = newSize;
            mView.getLayoutParams().width = newSize;
            // Create a border around the image.
            if (mView instanceof ImageView) {
                addPaddingToParent(mView, padding);
            }
        }

        @Override
        public boolean willChangeBounds() {
            return true;
        }
    }

    /**
     * Constructor for inflating from XML.
     */
    public PickerBitmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = (ImageView) findViewById(R.id.bitmap_view);
        mScrim = findViewById(R.id.scrim);
        mSelectedView = (ImageView) findViewById(R.id.selected);
        mUnselectedView = (ImageView) findViewById(R.id.unselected);
        mSpecialTileView = (ImageView) findViewById(R.id.special_tile);
        mSpecialTileLabel = (TextView) findViewById(R.id.special_tile_label);
    }

    public void initialize(PickerCategoryView categoryView) {
        mCategoryView = categoryView;
        mSelectionDelegate = mCategoryView.getSelectionDelegate();
        super.setSelectionDelegate(mSelectionDelegate);

        mSelectedView.setImageBitmap(mCategoryView.getSelectionBitmap(true));
        mUnselectedView.setImageBitmap(mCategoryView.getSelectionBitmap(false));

        mBorder = (int) getResources().getDimension(R.dimen.file_picker_selected_padding);
    }

    /**
     * Initialize the DownloadItemView. Must be called before the item can respond to click events.
     *
     * @param item      The item represented by this DownloadItemView.
     * @param thumbnail The Bitmap to use for the thumbnail or null.
     */
    public void initialize(PickerBitmap item, @Nullable Bitmap thumbnail, boolean placeholder) {
        mItem = item;
        setItem(item);
        setThumbnailBitmap(thumbnail);
        mImageLoaded = !placeholder;
        updateSelectionOverlays();

        setOnClickListener(this);
    }

    public void initializeSpecialTile() {
        int size = mCategoryView.getImageSize();
        Bitmap tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        tile.eraseColor(Color.rgb(0xEE, 0xEE, 0xEE));

        String label;
        if (mItem.type() == PickerBitmap.TileTypes.CAMERA) {
            mSpecialTileView.setImageBitmap(BitmapFactory.decodeResource(
                    // FLIP
                    mContext.getResources(), R.mipmap.ic_camera_alt_black_24dp));
                    //mContext.getResources(), R.drawable.ic_photo_camera));
            label = mContext.getString(R.string.file_picker_camera);  // TODOf localize
            mSpecialTileLabel.setText(label);
        } else {
            mSpecialTileView.setImageBitmap(BitmapFactory.decodeResource(
                    // FLIP
                    mContext.getResources(), R.mipmap.ic_collections_black_24dp));
                    //mContext.getResources(), R.drawable.ic_collections_black_24dp));
            label = mContext.getString(R.string.file_picker_browse);
            mSpecialTileLabel.setText(label);
        }
        mSpecialTileView.setVisibility(View.VISIBLE);
        mSpecialTileLabel.setVisibility(View.VISIBLE);

        initialize(mItem, tile, false);
    }

    /**
     * @param thumbnail The Bitmap to use for the icon ImageView.
     */
    public void setThumbnailBitmap(Bitmap thumbnail) {
        mIconView.setImageBitmap(thumbnail);
        mOriginalSize = thumbnail != null ? mIconView.getWidth() : 0;

        // If the tile has been selected before the bitmap has loaded, make sure it shows up with
        // a selection border and scrim on load.
        if (super.isChecked()) {
            mIconView.getLayoutParams().height = mOriginalSize - 2 * mBorder;
            mIconView.getLayoutParams().width = mOriginalSize - 2 * mBorder;
            addPaddingToParent(mIconView, mBorder);
            mScrim.setVisibility(View.VISIBLE);
        }

        mImageLoaded = true;
        updateSelectionOverlays();
    }

    @Override
    public void onClick() {
        Log.e("chromium", "PickerBitmapView::onClick, type: " + mItem.type());

        if (mItem.type() == PickerBitmap.TileTypes.GALLERY) {
            mCategoryView.showGallery();
            return;
        } else if (mItem.type() == PickerBitmap.TileTypes.CAMERA) {
            mCategoryView.showCamera();
            return;
        }

        mSelectionDelegate.toggleSelectionForItem(mItem);
        setChecked(!super.isChecked());
    }

    @Override
    public void setChecked(boolean checked) {
        if (mItem.type() != PickerBitmap.TileTypes.PICTURE) {
            return;
        }

        super.setChecked(checked);
        updateSelectionOverlays();
    }

    @Override
    public void onSelectionStateChange(List<PickerBitmap> selectedItems) {
        boolean selected = selectedItems.contains(mItem);

        if (mItem.type() != PickerBitmap.TileTypes.PICTURE) {
            if (selected) mSelectionDelegate.toggleSelectionForItem(mItem);
            return;
        }

        boolean checked = super.isChecked();

        if (!mCategoryView.isMultiSelect() && !selected && checked) {
            super.toggle();
        }

        updateSelectionOverlays();

        if (!mImageLoaded || selected == checked) {
            return;
        }

        int size = selected && !checked ? mOriginalSize - 2 * mBorder : mOriginalSize;
        if (size != mIconView.getWidth()) {
            ResizeWidthAnimation animation = new ResizeWidthAnimation(mIconView, mScrim, size);
            animation.setDuration(100);
            // TODO: Add MD interpolator
            // animation.setInterpolator((mContext, R.interpolator.fast_out_linear_in);
            mIconView.startAnimation(animation);
        }
    }

    private void updateSelectionOverlays() {
        if (mItem.type() != PickerBitmap.TileTypes.PICTURE) {
            return;
        }

        mSelectedView.setVisibility(super.isChecked() ? View.VISIBLE : View.GONE);

        // The visibility of the unselected image is a little more complex because we don't want
        // to show it when nothing is selected and also not on a blank canvas.
        boolean somethingSelected =
                mSelectionDelegate != null && mSelectionDelegate.isSelectionEnabled();
        if (!super.isChecked() && mImageLoaded && somethingSelected
                && mCategoryView.isMultiSelect()) {
            mUnselectedView.setVisibility(View.VISIBLE);
        } else {
            mUnselectedView.setVisibility(View.GONE);
        }

        boolean scrimVisibility = mSelectedView.getVisibility() == View.VISIBLE
                || mUnselectedView.getVisibility() == View.VISIBLE;
        mScrim.setVisibility(scrimVisibility ? View.VISIBLE : View.GONE);
    }

    public void setTextWithOverlay() {
        int photoSize = mCategoryView.getImageSize();
        Bitmap bitmap = Bitmap.createBitmap(photoSize, photoSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        int textSize = 72;
        paint.setTextSize(textSize);
        paint.setTextScaleX(1);
        String filePath = mItem.getFilePath();
        int dot = filePath.lastIndexOf(".");
        String extension = dot > -1 ? filePath.substring(dot) : "(no ext)";
        float width = paint.measureText(extension);
        canvas.drawText(extension, (photoSize - width) / 2, (photoSize - textSize) / 2, paint);
        mIconView.setImageBitmap(bitmap);
        updateSelectionOverlays();
    }
}
