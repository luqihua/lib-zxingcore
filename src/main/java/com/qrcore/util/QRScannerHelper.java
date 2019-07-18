package com.qrcore.util;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.DrawableRes;

import com.google.zxing.client.android.Intents;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.CaptureActivity;

import lu.zxingandroid.R;

/**
 * Author: luqihua
 * Time: 2017/7/11
 * Description: QRScannerHelper
 */

public class QRScannerHelper {

    private Activity mContext;
    private OnScannerCallBack mCallBack;

    private IntentIntegrator mIntent;

    public QRScannerHelper(Activity context) {
        this.mContext = context;
        mIntent =  new IntentIntegrator(mContext)
                .setOrientationLocked(false)
                .setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
                .setPrompt("将二维码/条码放入框内，即可自动扫描");
    }

    /**
     * 开启扫码界面
     */
    public void startScanner() {
        mIntent.initiateScan();
    }

    public void setFrameSlipSpeed(int time) {
        mIntent.setFrameSlipSpeed(time);
    }


    public void setFrameEdgeColor(int color) {
        mIntent.setFrameEdgeColor(color);
    }

    public void setSlipDrawable( @DrawableRes int drawableId) {
        mIntent.setSlipDrawable(drawableId);
    }

    /**
     * 设置扫码完成该的监听
     *
     * @param mCallBack
     */
    public void setCallBack(OnScannerCallBack mCallBack) {
        this.mCallBack = mCallBack;
    }

    /**
     * 该方法需要再activity的onActivityResult中调用获取返回的信息
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_CANCELED || mCallBack == null) return;
        String result;
        if (requestCode == IntentIntegrator.REQUEST_CODE && resultCode == CaptureActivity.SPOT_SUCCESS) {
            result = data.getStringExtra("data");
        } else {
            IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            result = intentResult.getContents();
        }
        mCallBack.onScannerBack(result);
    }


    public interface OnScannerCallBack {
        void onScannerBack(String result);
    }
}
