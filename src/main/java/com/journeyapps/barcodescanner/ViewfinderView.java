/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.journeyapps.barcodescanner;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.ResultPoint;

import java.util.ArrayList;
import java.util.List;

import lu.zxingandroid.R;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public class ViewfinderView extends View {
    protected static final String TAG = ViewfinderView.class.getSimpleName();

    protected static final int CURRENT_POINT_OPACITY = 0xA0;
    protected static final int MAX_RESULT_POINTS = 20;

    protected final Paint paint;
    protected Bitmap resultBitmap;
    protected final int maskColor;
    protected final int resultColor;
    protected final int laserColor;
    protected final int resultPointColor;
    protected int scannerAlpha;
    protected List<ResultPoint> possibleResultPoints;
    protected List<ResultPoint> lastPossibleResultPoints;
    protected CameraPreview cameraPreview;

    // Cache the framingRect and previewFramingRect, so that we can still draw it after the preview
    // stopped.
    protected Rect framingRect;
    protected Rect previewFramingRect;

    private int mWidth, mHeight;
    private ValueAnimator mSlipAnimator;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Resources resources = getResources();

        // Get setted attributes on view
        TypedArray attributes = getContext().obtainStyledAttributes(attrs, R.styleable.zxing_finder);

        this.maskColor = attributes.getColor(R.styleable.zxing_finder_zxing_viewfinder_mask,
                resources.getColor(R.color.zxing_viewfinder_mask));
        this.resultColor = attributes.getColor(R.styleable.zxing_finder_zxing_result_view,
                resources.getColor(R.color.zxing_result_view));
        this.laserColor = attributes.getColor(R.styleable.zxing_finder_zxing_viewfinder_laser,
                resources.getColor(R.color.zxing_viewfinder_laser));
        this.resultPointColor = attributes.getColor(R.styleable.zxing_finder_zxing_possible_result_points,
                resources.getColor(R.color.zxing_possible_result_points));

        attributes.recycle();

        scannerAlpha = 0;
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = null;


        /*----添加的初始化内容----*/
        customInit(context);

    }


    public void setCameraPreview(CameraPreview view) {
        this.cameraPreview = view;
        view.addStateListener(new CameraPreview.StateListener() {
            @Override
            public void previewSized() {
                refreshSizes();
                invalidate();
            }

            @Override
            public void previewStarted() {

            }

            @Override
            public void previewStopped() {

            }

            @Override
            public void cameraError(Exception error) {

            }

            @Override
            public void cameraClosed() {

            }
        });
    }

    protected void refreshSizes() {
        if (cameraPreview == null) {
            return;
        }
        Rect framingRect = cameraPreview.getFramingRect();
        Rect previewFramingRect = cameraPreview.getPreviewFramingRect();
        if (framingRect != null && previewFramingRect != null) {
            this.framingRect = framingRect;
            this.previewFramingRect = previewFramingRect;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h;
    }

    @SuppressLint("DrawAllocation")
    @Override
    public void onDraw(Canvas canvas) {
        refreshSizes();
        if (framingRect == null || previewFramingRect == null) {
            return;
        }
        Rect frame = framingRect;
        // TODO: 2017/7/13 画自己的东西
        customDraw(frame, canvas);

        // Draw the exterior (i.e. outside the framing rect) darkened
        paint.setColor(resultBitmap != null ? resultColor : maskColor);
        canvas.drawRect(0, 0, mWidth, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, mWidth, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, mWidth, mHeight, paint);

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, frame, paint);
        } else {
            /**
             * 扫描条滑动的动画驱动
             */
            if (mSlipAnimator == null) {
                mSlipAnimator = ValueAnimator.ofInt(frame.top, frame.bottom - mLineHeight)
                        .setDuration(mSlipCycleTime);
                mSlipAnimator.setRepeatCount(ValueAnimator.INFINITE);
                mSlipAnimator.setRepeatMode(ValueAnimator.RESTART);
                mSlipAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        mSlipLineY = (int) animation.getAnimatedValue();
                        invalidate();
                    }
                });
                mSlipAnimator.start();
            }
        }
    }


    /**
     * Only call from the UI thread.
     *
     * @param point a point to draw, relative to the preview frame
     */
    public void addPossibleResultPoint(ResultPoint point) {
        List<ResultPoint> points = possibleResultPoints;
        points.add(point);
        int size = points.size();
        if (size > MAX_RESULT_POINTS) {
            // trim it
            points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
        }
    }


    /*-----------------------自定义方法和属性--------------------------*/
    //画边框相关属性
    private Paint mLinePaint;//边框画笔
    private int mLineColor = Color.parseColor("#419afe");//边框的颜色

    //滑动条相关属性
    private Drawable mLineDrawable;//滑动条图片
    private Rect mLineReact;//滑动条区域
    private final int mLineHeight = 60;//滑动条的高度
    private int mSlipLineY;//滑动条绘制的Y起始位置
    private int mSlipCycleTime = 3000;//滑动条从头到尾为滑动一次的时间,设置该值可控制速度
    //文字相关属性
    private Paint mTextPaint;//画提示语的画笔
    private String mPromptText;//扫码的提示语
    private int mTextMargin;//提示语距离扫描框的大小

    /**
     * 改方法在构造方法中调用用来初始化属性
     *
     * @param context
     */
    private void customInit(Context context) {
        //初始化滑动线的画笔
        mLinePaint = new Paint();
        mLinePaint.setStyle(Paint.Style.FILL);
        mLinePaint.setStrokeWidth(20);
        mLinePaint.setColor(mLineColor);
        //初始化滑动条
        mLineDrawable = ContextCompat.getDrawable(context, R.drawable.lan);
        //初始化提示语的画笔
        mTextPaint = new Paint();
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(sp2px(14));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextMargin = sp2px(20);
    }


    /**
     * 传入提示语
     *
     * @param text
     */
    public void setPromptText(String text) {
        this.mPromptText = text;
    }


    /**
     * 该方法在onDraw中调用，用来画增加的东西
     *
     * @param frame
     * @param canvas
     */
    private void customDraw(Rect frame, Canvas canvas) {
        drawSlipLine(frame, canvas);
        drawEdge(frame, canvas);
        drawPromptText(frame, canvas);
    }


    /**
     * 画移动的短线
     *
     * @param frame
     * @param canvas
     */
    private void drawSlipLine(Rect frame, Canvas canvas) {
        if (mLineReact == null) {
            mLineReact = new Rect(frame.left + 5, frame.top, frame.right - 5, frame.top + mLineHeight);
        }else {
            mLineReact.offsetTo(frame.left + 5, mSlipLineY);
        }
        mLineDrawable.setBounds(mLineReact);
        mLineDrawable.draw(canvas);
    }

    /**
     * 画框边的四个角
     *
     * @param frame
     * @param canvas
     */
    private void drawEdge(Rect frame, Canvas canvas) {
        canvas.drawRect(frame.left - 10, frame.top, frame.left, frame.top + 50, mLinePaint);
        canvas.drawRect(frame.left - 10, frame.top - 10, frame.left + 50, frame.top, mLinePaint);
        canvas.drawRect(frame.right - 50, frame.top - 10, frame.right + 10, frame.top, mLinePaint);
        canvas.drawRect(frame.right, frame.top, frame.right + 10, frame.top + 50, mLinePaint);

        canvas.drawRect(frame.left - 10, frame.bottom - 50, frame.left, frame.bottom, mLinePaint);
        canvas.drawRect(frame.left - 10, frame.bottom, frame.left + 50, frame.bottom + 10, mLinePaint);
        canvas.drawRect(frame.right - 50, frame.bottom, frame.right, frame.bottom + 10, mLinePaint);
        canvas.drawRect(frame.right, frame.bottom - 50, frame.right + 10, frame.bottom + 10, mLinePaint);

    }

    /**
     * 画提示语
     *
     * @param frame
     * @param canvas
     */
    private void drawPromptText(Rect frame, Canvas canvas) {
        int startX = frame.left + frame.width() / 2;
        int startY = frame.bottom + mTextMargin;
        if (!TextUtils.isEmpty(mPromptText)) {
            canvas.drawText(mPromptText, startX, startY, mTextPaint);
        }
    }

    //设置从上到下滑动一次时间，可控制速度 默认必须 > 500毫秒
    public void setSlipCycleTime(int time) {
        if (time > 500) {
            this.mSlipCycleTime = time;
        }
    }


    //设置四边框颜色
    public void setLineColor(int color) {
        if (color != -1) {
            this.mLineColor = color;
            mLinePaint.setColor(mLineColor);
        }
    }

    //设置滑动条
    public void setSlipDrawable(int  drawableId) {
        try {
            Drawable drawable = ContextCompat.getDrawable(getContext(), drawableId);
            if (drawable != null) {
                mLineDrawable = drawable;
            }
        }catch (Resources.NotFoundException e){
           e.printStackTrace();
        }
    }

    /**
     * 将sp值转换为px值，保证文字大小不变
     *
     * @param spValue
     * @return
     */
    private int sp2px(float spValue) {
        final float scale = getContext().getResources().getDisplayMetrics().scaledDensity;
        return (int) (spValue * scale + 0.5f);
    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mSlipAnimator != null && mSlipAnimator.isRunning()) {
            mSlipAnimator.cancel();
            mSlipAnimator = null;
        }
    }
}
