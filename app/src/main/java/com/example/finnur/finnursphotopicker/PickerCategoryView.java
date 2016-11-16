// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// FLIP
//package org.chromium.chrome.browser;
package com.example.finnur.finnursphotopicker;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Environment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

// Chrome-specific:
/*
FLIP
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.ui.ThumbnailProviderImpl;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
*/

public class PickerCategoryView extends RelativeLayout implements FileEnumWorkerTask.FilesEnumeratedCallback {
    private Context mContext;
    private PickerAdapter mPickerAdapter;
    private List<PickerBitmap> mPickerBitmaps;

    private RecyclerView mRecyclerView;

    private SelectionDelegate<PickerBitmap> mSelectionDelegate;

    private ThumbnailProviderImpl mThumbnailProvider;

    private LruCache<String, Bitmap> mUglyBitmaps;
    private LruCache<String, Bitmap> mPrettyBitmaps;

    private int mColumns = 3;

    // Maximum number of bitmaps to show.
    private int mMaxImages = 1;

    // The size of the bitmaps (equal length for width and height).
    private int mImageSize = 10;

    private Bitmap mBitmapSelected;
    private Bitmap mBitmapUnselected;

    // A worker task for asynchronously enumerating files off the main thread.
    private FileEnumWorkerTask mWorkerTask;

    private class RecyclerViewItemDecoration extends RecyclerView.ItemDecoration {
        private static final int PADDING = 18;

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                                   RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);

            if (position % mColumns != 0) {
                outRect.left = PADDING;
            }

            if (position < parent.getAdapter().getItemCount() - mColumns) {
                outRect.bottom = PADDING;
            }
        }
    }

    public PickerCategoryView(Context context) {
        super(context);
        init(context);
    }

    public PickerCategoryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PickerCategoryView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        inflate(mContext, R.layout.picker_category_view, this);
    }

    public int getMaxImagesShown() { return mMaxImages; }
    public int getImageSize() { return mImageSize; }
    public SelectionDelegate<PickerBitmap> getSelectionDelegate() { return mSelectionDelegate; }
    public List<PickerBitmap> getPickerBitmaps() { return mPickerBitmaps; }
    public ThumbnailProviderImpl getThumbnailProvider() { return mThumbnailProvider; }
    public LruCache<String, Bitmap> getUglyBitmaps() { return mUglyBitmaps; }
    public LruCache<String, Bitmap> getPrettyBitmaps() { return mPrettyBitmaps; }

    public Bitmap getSelectionBitmap(boolean selected) {
        if (selected)
            return mBitmapSelected;
        else
            return mBitmapUnselected;
    }

    public void setInitialState(String path, SelectionDelegate<PickerBitmap> selectionDelegate) {
        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mRecyclerView.addItemDecoration(new RecyclerViewItemDecoration());
        mSelectionDelegate = selectionDelegate;

        // FLIP
        mBitmapSelected = BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.ic_check_circle_black_24dp);
        mBitmapUnselected = BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.ic_donut_large_black_24dp);
        //mBitmapSelected = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_share_white_24dp);
        //mBitmapUnselected = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_arrow_back_white_24dp);

        mMaxImages = 40 * mColumns;
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        mImageSize = metrics.widthPixels / mColumns;

        // The thumbnail clamps the maximum of the smaller side, we need to clamp
        // down the maximum of the larger side, so we flip the sizes.
        mThumbnailProvider = new ThumbnailProviderImpl(mImageSize * 3 / 4);

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSizeLarge = maxMemory / 2; // 1/2th of the available memory.
        final int cacheSizeSmall = maxMemory / 8; // 1/8th of the available memory.
        Log.e("chromium", "Cache sizes: " + cacheSizeLarge + " " + cacheSizeSmall);
        mUglyBitmaps = new LruCache<String, Bitmap>(cacheSizeSmall) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
        mPrettyBitmaps = new LruCache<String, Bitmap>(cacheSizeLarge) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        mPickerAdapter = new PickerAdapter(mContext, this);
        RecyclerView.LayoutManager mLayoutManager = new GridLayoutManager(mContext, mColumns);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());

        prepareBitmaps(path);
    }

    public void filesEnumeratedCallback(List<PickerBitmap> files) {
        mPickerBitmaps = files;
        if (files != null && files.size() > 0) {
            mRecyclerView.setAdapter(mPickerAdapter);
            //mPickerAdapter.notifyDataSetChanged();
        } else {
            setVisibility(View.GONE);
        }
    }

    private void prepareBitmaps(String path) {
        if (mWorkerTask != null)
            mWorkerTask.cancel(true);

        mWorkerTask = new FileEnumWorkerTask(this);
        mWorkerTask.execute(path);
    }
}
