package com.example.gesturelib;

import static com.example.gesturelib.StateMode.DETECTION_MODE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.zeewain.zeepose.HolisticInfo;
import com.zeewain.zeepose.PoseInfo;
import com.zeewain.zeepose.ZeewainPose;
import com.zeewain.zeepose.ZwnConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;


public class GestureManager implements Camera2Listener {
    private static final String TAG = "GestureManagerService";
    private Context context;

    @SuppressLint("StaticFieldLeak")
    private static GestureManager instance;

    private GestureManager(Context context) {
        this.context = context;
    }

    public static GestureManager getInstance(Context context) {
        if (instance == null) {
            instance = new GestureManager(context);
        }
        return instance;
    }

    public final ZeewainPose zeewainPose = new ZeewainPose();
    private static String modelsDirPath;
    private static String licenseFilePath;
    private String akCode;
    private String skCode;
    private String authUri;

    private PoseInfo[] poseInfoOverlayArray;
    private PoseInfo[] poseInfoOrgArray;

    public HolisticInfo[] holisticInfoArray;
    public HolisticInfo[] holisticInfoOrgArray;

    private StateMode currentStateMode = DETECTION_MODE;

    private Rect detectionRegionOrgRect = null;
    private Rect detectionRegionRect = null;

    public boolean isDrawOverlayPoint = true;

    private float scaleX = 1.0f;
    private float scaleY = 1.0f;

    private OverlayView overlayView;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final int nativeWidth = 640;
    private final int nativeHeight = 480;
    private final int targetWidth = 1920;
    private final int targetHeight = 1080;

    public void setHolisticInfoArray(HolisticInfo[] holisticInfoArray) {
        this.holisticInfoArray = holisticInfoArray;
    }

    public void setHolisticInfoOrgArray(HolisticInfo[] holisticInfoOrgArray) {
        this.holisticInfoOrgArray = holisticInfoOrgArray;
    }

    private final List<LocationEnum> locationEnumList = new ArrayList<>();

    private final int REQUEST_CODE_PERMISSIONS = 1;
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

//    static {
//        System.loadLibrary("GestureEstimate");
//    }

    private ExecutorService fixedThreadPool;

    private synchronized void addLocationEnum(LocationEnum locationEnum) {
        for (int i = 0; i < locationEnumList.size(); i++) {
            if (locationEnumList.get(i) == locationEnum) {
                locationEnumList.remove(i);
                break;
            }
        }
        locationEnumList.add(locationEnum);
    }

    public void releaseAllZee() {
        for (int i = 0; i < locationEnumList.size(); i++) {
            LocationEnum locationEnum = locationEnumList.get(i);
            if (LocationEnum.PERSONPOSE == locationEnum) {
                zeewainPose.releasePose();
            } else if (LocationEnum.SINGLEPOSE == locationEnum) {
                zeewainPose.releaseSinglePose();
            } else if (LocationEnum.FACE == locationEnum) {
                zeewainPose.releaseFace();
            } else if (LocationEnum.HAND == locationEnum) {
                zeewainPose.releaseHand();
            } else if (LocationEnum.HOLISTIC == locationEnum) {
                zeewainPose.releaseHolistic();
            } else if (LocationEnum.SEGMENT == locationEnum) {
                zeewainPose.releaseSegment();
            } else if (LocationEnum.POSE3D2D == locationEnum) {
                zeewainPose.releasePose3D2D();
            } else if (LocationEnum.HOLISTIC3D2D == locationEnum) {
                zeewainPose.releaseHolistic3D2D();
            }
        }
        locationEnumList.clear();
    }

    public int initPersonPose(int modelType, int deviceType, int threadNum) {
        int result = zeewainPose.initPose(new ZwnConfig(modelsDirPath, deviceType, threadNum, modelType));
        if (result == 0) {
            addLocationEnum(LocationEnum.PERSONPOSE);
        }
        return result;
    }

    public synchronized int initHolistic(int modelType, int deviceType, int threadNum) {
        int result = zeewainPose.initHolistic(new ZwnConfig(modelsDirPath, deviceType, threadNum, modelType));
        if (result == 0) {
            addLocationEnum(LocationEnum.HOLISTIC);
        }
        return result;
    }

    public void init(List<String> extras, OverlayView overlayView) {
        this.overlayView = overlayView;

        licenseFilePath = new File(context.getFilesDir(), "zeewain").getAbsolutePath();
        modelsDirPath = new File(context.getFilesDir(), "models").getAbsolutePath();
        String akCodeExtra = extras.get(0);
        String authUriExtra = extras.get(2);

        if (!TextUtils.isEmpty(akCodeExtra) && !TextUtils.isEmpty(authUriExtra)) {

            String skCodeExtra = extras.get(1);

            try {
                int authResult = zeewainPose.setAuthOnline(akCodeExtra, skCodeExtra, authUriExtra, licenseFilePath);
                if (authResult == 0) {
                    this.akCode = akCodeExtra;
                    this.skCode = skCodeExtra;
                    this.authUri = authUriExtra;
                    copyModel();
                    int result = initPersonPose(0, 0, 2);
                    int resultHolistic = initHolistic(0, 0, 2);
                    zeewainPose.setHolisticFaceLandmarkStatus(false);
                    zeewainPose.setDynamicCropStatus(true);
                    if (BuildConfig.DEBUG) {
                        setDrawOverlayPoint(true);
                        setOverlayPaintStyle(6f, new int[]{0xFFFB2000, 0xFF00FF00, 0xFFFF3399}, 4f, true);
                        setOverlayShowPosePoint(new int[]{0, 1, 3}, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13});
                    }
                } else {
                    throw new RuntimeException("Zee wainPose auth failed!");
                }
            } catch (Exception e) {
                Log.d(TAG, "*** authZeeWainPose error " + e);
            }
        } else if (locationEnumList.size() >= 2) {
            Log.e("xxx", "handleIntent()-------222222----->>>>");
        } else if (!TextUtils.isEmpty(akCode) && !TextUtils.isEmpty(authUri)) {
            Log.e("xxx", "handleIntent()-------3333333333---->>>>");
            int authResult = zeewainPose.setAuthOnline(akCode, skCode, authUri, licenseFilePath);
            if (authResult == 0) {
                copyModel();
                int result = initPersonPose(0, 0, 2);
                int resultHolistic = initHolistic(0, 0, 2);
                zeewainPose.setHolisticFaceLandmarkStatus(false);
                zeewainPose.setDynamicCropStatus(true);
                if (BuildConfig.DEBUG) {
                    setDrawOverlayPoint(true);
                    setOverlayPaintStyle(6f, new int[]{0xFFFB2000, 0xFF00FF00, 0xFFFF3399}, 4f, true);
                    setOverlayShowPosePoint(new int[]{0, 1, 3}, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13});
                }
            } else {
                throw new RuntimeException("Zee wainPose auth failed!");
            }
        }
    }


    private void copyModel() {
        boolean isDone = CommonUtils.copyFilesFromAssetsTo(context, zeewainPose.getModelNameLists(), modelsDirPath);
        if (!isDone) {
            Log.e(TAG, "拷贝模型失败！");
        }
    }

    @Override
    public void method(Bitmap bitmap) {
        if (locationEnumList.size() > 0) {
            personPoseMethod(bitmap);
        }
    }


    public void onCameraOpened() {
        currentStateMode = StateMode.DETECTION_MODE;

        handler.post(() -> {
            scaleX = targetWidth * 1f / nativeWidth;
            scaleY = targetHeight * 1f / nativeHeight;
//            updateDetectionRegionRect();
        });
    }

    private void updateDetectionRegionRect() {
        int left = (int) (targetWidth / 4f);
        int top = (int) (targetHeight / 10f);
        int right = (int) (targetWidth / 4f);
        int bottom = (int) (targetHeight / 10f);
        detectionRegionRect = new Rect(left, top, right, bottom);

        if (BuildConfig.DEBUG) {
            showOverlayDetectionRegion(true);
        }

        detectionRegionOrgRect = new Rect((int) (left / scaleX), (int) (top / scaleY), (int) (right / scaleX), (int) (bottom / scaleY));
    }

    private void personPoseMethod(Bitmap bitmap) {
        if (DETECTION_MODE == currentStateMode) {
            poseInfoOrgArray = zeewainPose.getPoseInfo(bitmap);

            if (isDrawOverlayPoint) {
                handleTransformPoseInfoResult(transformPoseToOverlay(poseInfoOrgArray));
            }
        }
    }

    private void handleTransformPoseInfoResult(PoseInfo[] poseInfoArray) {
        if (poseInfoArray != null && poseInfoArray.length > 0) {
            poseInfoOverlayArray = poseInfoArray;
            handler.post(() -> {
                if (overlayView != null)
                    overlayView.drawPosePoint(poseInfoOverlayArray);
            });
        } else {
            poseInfoOverlayArray = null;
            handler.post(() -> {
                if (overlayView != null) {
                    overlayView.clear();
                }
            });
        }
    }

    private PoseInfo[] transformPoseToOverlay(PoseInfo[] poseInfoOrgArray) {
        if (poseInfoOrgArray != null && poseInfoOrgArray.length > 0) {
            PoseInfo[] poseInfoOverlayArray = new PoseInfo[poseInfoOrgArray.length];

            for (int j = 0; j < poseInfoOrgArray.length; j++) {
                poseInfoOverlayArray[j] = ZeePoseWrapper.clonePoseInfo(poseInfoOrgArray[j]);

                if (poseInfoOverlayArray[j].landmarks != null) {
                    for (int i = 0; i < poseInfoOverlayArray[j].landmarks.length; i++) {
                        poseInfoOverlayArray[j].landmarks[i].x = poseInfoOverlayArray[j].landmarks[i].x * scaleX;
                        poseInfoOverlayArray[j].landmarks[i].y = poseInfoOverlayArray[j].landmarks[i].y * scaleY;
                    }
                }

                if (poseInfoOverlayArray[j].rect != null) {
                    poseInfoOverlayArray[j].rect.x = (int) (poseInfoOverlayArray[j].rect.x * scaleX);
                    poseInfoOverlayArray[j].rect.y = (int) (poseInfoOverlayArray[j].rect.y * scaleY);
                    poseInfoOverlayArray[j].rect.width = (int) (poseInfoOverlayArray[j].rect.width * scaleX);
                    poseInfoOverlayArray[j].rect.height = (int) (poseInfoOverlayArray[j].rect.height * scaleY);
                }
            }
            return poseInfoOverlayArray;
        }
        return null;
    }

    public void setDrawOverlayPoint(final boolean isDrawOverlayPoint) {
        handler.post(() -> {
            this.isDrawOverlayPoint = isDrawOverlayPoint;
            if (overlayView != null) {
                overlayView.clear();
                if (isDrawOverlayPoint) {
                    overlayView.setVisibility(View.VISIBLE);
                } else {
                    overlayView.setVisibility(View.GONE);
                }
            }
        });
    }

    public void setOverlayPaintStyle(final float pointRadius, final int[] paintColor,
                                     final float paintStrokeWidth, final boolean showLine) {
        handler.post(() -> {
            if (overlayView != null) {
                overlayView.setPaintStyle(pointRadius, paintColor, paintStrokeWidth, showLine);
            }
        });
    }

    public void setOverlayShowPosePoint(final int[] poseArrayIndex, final int[] posePoint) {
        handler.post(() -> {
            if (overlayView != null) {
                overlayView.showPoseArrayIndexMap.clear();
                if (poseArrayIndex != null) {
                    for (int j : poseArrayIndex) {
                        overlayView.showPoseArrayIndexMap.put(j, j);
                    }
                }

                overlayView.showPosePointMap.clear();
                if (posePoint != null) {
                    for (int j : posePoint) {
                        overlayView.showPosePointMap.put(j, j);
                    }
                }
            }
        });
    }

    public void showOverlayDetectionRegion(final boolean showDetectionRegion) {
        handler.post(() -> {
            if (overlayView != null) {
                if (showDetectionRegion) {
                    overlayView.drawDetectionRegion(detectionRegionRect);
                } else {
                    overlayView.drawDetectionRegion(null);
                }
            }
        });
    }

    private boolean isPosePointInRect(PoseInfo poseInfo, Rect rect) {
        if (poseInfo != null && poseInfo.landmarks != null) {
            if (isPointInRect(poseInfo.landmarks[3].x, poseInfo.landmarks[3].y, rect)
                    && isPointInRect(poseInfo.landmarks[4].x, poseInfo.landmarks[4].y, rect)
                    && isPointInRect(poseInfo.landmarks[5].x, poseInfo.landmarks[5].y, rect)
                    && isPointInRect(poseInfo.landmarks[6].x, poseInfo.landmarks[6].y, rect)
                    /*&& isPointInRect(poseInfo.landmarks[13].x, poseInfo.landmarks[13].y, rect)
                    && isPointInRect(poseInfo.landmarks[14].x, poseInfo.landmarks[14].y, rect)*/) {

                if (poseInfo.landmarks[4].x < poseInfo.landmarks[3].x
                        && poseInfo.landmarks[6].x < poseInfo.landmarks[5].x) {//面向摄像头
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPointInRect(float x, float y, Rect rect) {
        return (x > rect.left && x < rect.right && y > rect.top && y < rect.bottom);
    }
}
