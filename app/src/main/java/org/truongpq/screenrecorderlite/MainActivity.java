package org.truongpq.screenrecorderlite;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;

import java.io.File;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private static final int REQUEST_CODE_CAPTURE_PERM = 1234;
    private static final int WRITE_EXTERNAL_STORAGE = 123;
    private MediaProjectionManager mediaProjectionManager;
    private ToggleButton toggleRecorder;

    private AdView mAdView;
    private InterstitialAd mInterstitialAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mInterstitialAd = new InterstitialAd(this);

        // set the ad unit ID
        mInterstitialAd.setAdUnitId(getString(R.string.id_full_banner));

        AdRequest adRequest = new AdRequest.Builder().build();

        // Load ads into Interstitial Ads
        mInterstitialAd.loadAd(adRequest);

        mInterstitialAd.setAdListener(new AdListener() {
            public void onAdLoaded() {
                showInterstitial();
            }
        });

        File folder = new File(Environment.getExternalStorageDirectory() + "/Screen Recorder");
        if (!folder.isDirectory()) {
            folder.mkdirs();
        }

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        initView();
    }

    private void showInterstitial() {
        if (mInterstitialAd.isLoaded()) {
            mInterstitialAd.show();
        }
    }

    private void initView() {
        toggleRecorder = (ToggleButton) findViewById(R.id.toggle_recorder);
        if (isRecorderServiceRunning()) {
            toggleRecorder.setChecked(true);
        } else {
            toggleRecorder.setChecked(false);
        }

        toggleRecorder.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (!b) {
                    toggleRecorder.setChecked(false);
                    stopService(new Intent(getApplicationContext(), RecorderService.class));
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE);
                    } else {
                        toggleRecorder.setChecked(true);
                        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_CAPTURE_PERM);
                    }
                }
            }
        });

        TextView tvInfor = (TextView) findViewById(R.id.tv_infor);
        tvInfor.setOnClickListener(this);

        TextView tvRate = (TextView) findViewById(R.id.tv_rate);
        tvRate.setOnClickListener(this);

        TextView tvUpgrade = (TextView) findViewById(R.id.tv_upgrade);
        tvUpgrade.setOnClickListener(this);

        AdView mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case WRITE_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    toggleRecorder.setChecked(true);
                    startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_CAPTURE_PERM);
                } else {
                    MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                            .content("Allow we to access photos, media, and files on your device?")
                            .negativeText("Cancel")
                            .positiveText("Settings")
                            .negativeColor(ContextCompat.getColor(this, R.color.negative_material_dialog))
                            .positiveColor(ContextCompat.getColor(this, R.color.positive_material_dialog))
                            .backgroundColor(ContextCompat.getColor(this, R.color.colorText))
                            .titleColor(ContextCompat.getColor(this, R.color.title_material_dialog))
                            .contentColor(ContextCompat.getColor(this, R.color.content_material_dialog));
                    final MaterialDialog dialog = builder.build();
                    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                    View positive = dialog.getActionButton(DialogAction.POSITIVE);
                    positive.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            Uri uri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                            dialog.dismiss();
                        }
                    });
                    dialog.show();
                }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (REQUEST_CODE_CAPTURE_PERM == requestCode) {
            if (resultCode == RESULT_OK) {
                RecorderService.mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intent);
                startService(new Intent(getApplicationContext(), RecorderService.class));
            } else {
                toggleRecorder.setChecked(false);
            }
        }
    }

    private boolean isRecorderServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (RecorderService.class.getName().equals(service.service.getClassName()))
                return true;
        }
        return false;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.tv_infor:
                new MaterialDialog.Builder(this)
                        .title(getResources().getString(R.string.app_name))
                        .content("Version " + BuildConfig.VERSION_NAME)
                        .icon(ContextCompat.getDrawable(this, R.mipmap.ic_launcher))
                        .positiveText("Ok")
                        .positiveColor(ContextCompat.getColor(this, R.color.positive_material_dialog))
                        .backgroundColor(ContextCompat.getColor(this, R.color.colorText))
                        .titleColor(ContextCompat.getColor(this, R.color.content_material_dialog))
                        .contentColor(ContextCompat.getColor(this, R.color.content_material_dialog))
                        .show();
                break;
            case R.id.tv_rate:
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
                } catch (android.content.ActivityNotFoundException anfe) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
                }
                break;
            case R.id.tv_upgrade:
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.truongpq.screenrecorder")));
                } catch (android.content.ActivityNotFoundException anfe) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=org.truongpq.screenrecorder")));
                }
        }
    }

    @Override
    public void onPause() {
        if (mAdView != null) {
            mAdView.pause();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAdView != null) {
            mAdView.resume();
        }
    }

    @Override
    public void onDestroy() {
        if (mAdView != null) {
            mAdView.destroy();
        }
        super.onDestroy();
    }
}
