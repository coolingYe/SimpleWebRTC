package com.example.gesturelib;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.zeewain.zeepose.FaceInfo;
import com.zeewain.zeepose.HandInfo;
import com.zeewain.zeepose.Pose3D2DInfo;
import com.zeewain.zeepose.PoseInfo;
import com.zeewain.zeepose.base.Point2f;
import com.zeewain.zeepose.base.Point3f;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class OverlayView extends View {

    private static final String TAG = "zeepose";
    private static final int HANDPOINTNUM = 21;
    private static final int POSEPOINTNUM1 = 17;
    private static final int POSEPOINTNUM2 = 29;
    private static final int POSEPOINTNUM3 = 33;

    private FaceInfo[] faceInfos = null;
    private HandInfo[] handInfos = null;
    private PoseInfo[] poseInfos = null;
    public int[] paintColors = null;
    private Rect detectionRegion = null;

    public ConcurrentHashMap<Integer, Integer> showPoseArrayIndexMap = new ConcurrentHashMap<>();
    public ConcurrentHashMap<Integer, Integer> showPosePointMap = new ConcurrentHashMap<>();

    private ArrayList<Point3f> handPoint;
    private final int[][] handLineIndex = new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 4}, {0, 5},
            {5, 6}, {6, 7}, {7, 8}, {5, 9}, {9, 10}, {10, 11}, {11, 12}, {9, 13}, {13, 14},
            {14, 15}, {15, 16}, {13, 17}, {0, 17}, {17, 18}, {18, 19}, {19, 20}};

    private ArrayList<Point3f> posePoint;
    private final int[][] pose17Index = new int[][] {{0, 2}, {2, 4}, {0, 1}, {1, 3},
            {5, 6}, {5, 7}, {7, 9},{6, 8}, {8, 10},
            {6, 12}, {5, 11},
            {11, 12}, {11, 13}, {13, 15}, {12, 14}, {14, 16}
    };

    private final int[][] pose29Index = new int[][] {{0, 2}, {2, 4}, {0, 1}, {1, 3},
            {5, 6}, {5, 7}, {7, 9},{9, 15}, {9, 13}, {9, 11},
            {6, 8}, {8, 10}, {10, 12}, {10, 14}, {10, 16},
            {17, 18},
            {6, 18}, {18, 20}, {20, 22},
            {5, 17}, {17, 19}, {19, 21},
            {22, 24}, {24, 26}, {22, 28},
            {21, 23}, {21, 25}, {21, 27}
    };

    private final int[][] pose33Index = new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 7},
            {0, 4}, {4, 5}, {5, 6}, {6, 8},
            {9, 10},
            {11, 13}, {13, 15}, {15, 21}, {15, 17}, {15, 19}, {17, 19},
            {12, 14}, {14, 16}, {16, 22}, {16, 18}, {16, 20}, {20, 22},
            {11, 23}, {23, 25}, {25, 27}, {27, 29}, {27, 31}, {29, 31},
            {12, 24}, {24, 26}, {26, 28}, {28, 30}, {28, 32}, {30, 32},
            {11, 12}, {23, 24}
    };

    private LocationEnum location = null;
    private final Paint circlePaint = new Paint();
    private final Paint rectPaint = new Paint();
    private final Paint linePaint = new Paint();
    private final Paint detectionRegionPaint = new Paint();
    private float pointRadius = 6f;
    private boolean showLine = false;
    private boolean showHeadPoint = false;

    public OverlayView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public void initOverlay(LocationEnum locationEnum) {
        location = locationEnum;

        circlePaint.setAntiAlias(true);
        circlePaint.setColor(Color.GREEN);
        circlePaint.setStrokeWidth(4.0f);
        circlePaint.setStyle(Paint.Style.FILL);

        rectPaint.setAntiAlias(true);
        rectPaint.setColor(Color.GREEN);
        rectPaint.setStrokeWidth(4.0f);
        rectPaint.setStyle(Paint.Style.STROKE);

        linePaint.setAntiAlias(true);
        linePaint.setColor(Color.GREEN);
        linePaint.setStrokeWidth(4.0f);
        linePaint.setStyle(Paint.Style.FILL);

        detectionRegionPaint.setAntiAlias(true);
        detectionRegionPaint.setColor(Color.YELLOW);
        detectionRegionPaint.setStrokeWidth(4.0f);
        detectionRegionPaint.setStyle(Paint.Style.STROKE);

        paintColors = new int[]{Color.GREEN};
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(detectionRegion != null){
            canvas.drawRect(detectionRegion, detectionRegionPaint);
        }
        switch (location) {
            case FACE:
                if (faceInfos != null) {
                    for (FaceInfo faceInfo : faceInfos) {
                        if (faceInfo.rect != null) {
                            float x1 = faceInfo.rect.x;
                            float y1 = faceInfo.rect.y;
                            float x2 = x1 + faceInfo.rect.width;
                            float y2 = y1 + faceInfo.rect.height;
                            canvas.drawRect(new Rect((int) x1, (int) y1, (int) x2, (int) y2), rectPaint);
                        }
                    }
                }
                break;
            case HAND:
                if (handInfos != null) {
                    for (HandInfo handInfo : handInfos) {
                        if (handPoint != null) {
                            handPoint.clear();
                        }
                        handPoint = new ArrayList<>();
                        Point2f[] landmarks = handInfo.landmarks;
                        if (landmarks != null)   //由于初始化可选择不进行手部关键点检测，因此需要校验该landmarks
                        {
                            for (Point2f landmark : landmarks) {
                                float x = landmark.x;
                                float y = landmark.y;
                                canvas.drawCircle(x, y, pointRadius, circlePaint);
                                handPoint.add(new Point3f(x, y, 0));
                            }
                        }
                        if (handPoint.size() == HANDPOINTNUM) {
                            for (int[] index : handLineIndex) {
                                canvas.drawLine(handPoint.get(index[0]).x, handPoint.get(index[0]).y,
                                        handPoint.get(index[1]).x, handPoint.get(index[1]).y, linePaint);
                            }
                        }
                        if (handInfo.rect != null) {
                            float x1 = handInfo.rect.x;
                            float y1 = handInfo.rect.y;
                            float x2 = x1 + handInfo.rect.width;
                            float y2 = y1 + handInfo.rect.height;
                            canvas.drawRect(new Rect((int) x1, (int) y1, (int) x2, (int) y2), rectPaint);
                        }
                    }
                }
                break;
            case SINGLEPOSE:
            case PERSONPOSE:
                if (poseInfos != null) {
                    for (int j=0; j<poseInfos.length; j++) {
                        if(!showPoseArrayIndexMap.contains(j)) continue;
                        circlePaint.setColor(paintColors[j % paintColors.length]);
                        linePaint.setColor(paintColors[j % paintColors.length]);
                        PoseInfo poseInfo = poseInfos[j];
                        if (posePoint != null) {
                            posePoint.clear();
                        }
                        posePoint = new ArrayList<>();
                        Point2f[] landmarks = poseInfo.landmarks;
                        if(landmarks != null) {
                            for (int i = 0; i < landmarks.length; i++) {
                                float x = landmarks[i].x;
                                float y = landmarks[i].y;
                                posePoint.add(new Point3f(x, y, 0));
                                if(showPosePointMap.contains(i)) {
                                    canvas.drawCircle(x, y, pointRadius, circlePaint);
                                }
                            }
                        }

                        if(showLine) {
                            if (posePoint.size() == POSEPOINTNUM1) {
                                int count = 0;
                                for (int[] index : pose17Index) {
                                    count ++;
                                    if(!showHeadPoint && count <= 4) continue;
                                    canvas.drawLine(posePoint.get(index[0]).x, posePoint.get(index[0]).y,
                                            posePoint.get(index[1]).x, posePoint.get(index[1]).y, linePaint);

                                }
                            } else if (posePoint.size() == POSEPOINTNUM2) {
                                int count = 0;
                                for (int[] index : pose29Index) {
                                    count ++;
                                    if(!showHeadPoint && count <= 4) continue;
                                    canvas.drawLine(posePoint.get(index[0]).x, posePoint.get(index[0]).y,
                                            posePoint.get(index[1]).x, posePoint.get(index[1]).y, linePaint);
                                }
                            } else if (posePoint.size() == POSEPOINTNUM3) {
                                for (int[] index : pose33Index) {
                                    canvas.drawLine(posePoint.get(index[0]).x, posePoint.get(index[0]).y,
                                            posePoint.get(index[1]).x, posePoint.get(index[1]).y, linePaint);
                                }
                            }
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    public void drawDetectionRegion(Rect detectionRegion){
        this.detectionRegion = detectionRegion;
        this.invalidate();
    }

    public void drawFacePoint(FaceInfo[] faceInfos){
        location = LocationEnum.FACE;
        this.poseInfos = null;
        this.handInfos = null;
        this.faceInfos = faceInfos;
        this.invalidate();
    }

    public void drawHandPoint(HandInfo[] handInfos) {
        location = LocationEnum.HAND;
        this.poseInfos = null;
        this.faceInfos = null;
        this.handInfos = handInfos;
        this.invalidate();
    }

    public void drawPosePoint(PoseInfo[] poseInfos) {
        location = LocationEnum.PERSONPOSE;
        this.faceInfos = null;
        this.handInfos = null;
        this.poseInfos = poseInfos;
        this.invalidate();
    }

    public void drawPose3D2DPoint(Pose3D2DInfo[] pose3D2DInfos) {
        location = LocationEnum.PERSONPOSE;
        this.faceInfos = null;
        this.handInfos = null;
        if(pose3D2DInfos != null) {
            PoseInfo[] poseInfos = new PoseInfo[pose3D2DInfos.length];
            for (int i = 0; i < pose3D2DInfos.length; i++) {
                Pose3D2DInfo pose3D2DInfo = pose3D2DInfos[i];
                poseInfos[i] = new PoseInfo(pose3D2DInfo.rect, pose3D2DInfo.trackId, pose3D2DInfo.landmarks2D, pose3D2DInfo.scores2D);
            }
            this.poseInfos = poseInfos;
            this.invalidate();
        }
    }



    public void setPaintStyle(float pointRadius, int[] paintColors, float paintStrokeWidth, boolean showLine){
        circlePaint.setColor(paintColors[0]);
        circlePaint.setStrokeWidth(paintStrokeWidth);
        this.pointRadius = pointRadius;
        this.showLine = showLine;

        rectPaint.setColor(paintColors[0]);
        rectPaint.setStrokeWidth(paintStrokeWidth);

        linePaint.setColor(paintColors[0]);
        linePaint.setStrokeWidth(paintStrokeWidth);
        this.paintColors = paintColors;
    }

    public void setShowHeadPoint(boolean showHeadPoint){
        this.showHeadPoint = showHeadPoint;
    }

    public void clear(){
        this.poseInfos = null;
        this.faceInfos = null;
        this.handInfos = null;
        this.invalidate();
    }

    public void release() {
        faceInfos = null;
        handInfos = null;
        poseInfos = null;
        if (handPoint != null) {
            handPoint.clear();
        }
    }
}