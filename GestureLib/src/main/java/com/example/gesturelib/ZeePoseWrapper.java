package com.example.gesturelib;

import com.zeewain.zeepose.FaceInfo;
import com.zeewain.zeepose.Hand3D2DInfo;
import com.zeewain.zeepose.HandInfo;
import com.zeewain.zeepose.Pose3D2DInfo;
import com.zeewain.zeepose.PoseInfo;
import com.zeewain.zeepose.base.Point2f;
import com.zeewain.zeepose.base.Point3f;
import com.zeewain.zeepose.base.Rect;

public class ZeePoseWrapper {

    public static PoseInfo clonePoseInfo(PoseInfo poseInfo){
        Point2f[] point2fs = null;
        if(poseInfo.landmarks != null){
            point2fs = new Point2f[poseInfo.landmarks.length];
            for(int i=0; i<poseInfo.landmarks.length; i++){
                point2fs[i] = new Point2f(poseInfo.landmarks[i].x, poseInfo.landmarks[i].y);
            }
        }
        Rect rect = null;
        if(poseInfo.rect != null) {
            rect = new Rect(poseInfo.rect.x, poseInfo.rect.y, poseInfo.rect.width, poseInfo.rect.height);
        }
        return new PoseInfo(rect, poseInfo.trackId, point2fs, poseInfo.scores);
    }

    public static Pose3D2DInfo clonePose3D2DInfo(Pose3D2DInfo pose3D2DInfo){
        Point2f[] point2fs = null;
        if(pose3D2DInfo.landmarks2D != null){
            point2fs = new Point2f[pose3D2DInfo.landmarks2D.length];
            for(int i=0; i<pose3D2DInfo.landmarks2D.length; i++){
                point2fs[i] = new Point2f(pose3D2DInfo.landmarks2D[i].x, pose3D2DInfo.landmarks2D[i].y);
            }
        }

        Point3f[] point3fs = null;
        if(pose3D2DInfo.landmarks3D != null){
            point3fs = new Point3f[pose3D2DInfo.landmarks3D.length];
            for(int i=0; i<pose3D2DInfo.landmarks3D.length; i++){
                point3fs[i] = new Point3f(pose3D2DInfo.landmarks3D[i].x, pose3D2DInfo.landmarks3D[i].y, pose3D2DInfo.landmarks3D[i].z);
            }
        }

        Rect rect = null;
        if(pose3D2DInfo.rect != null) {
            rect = new Rect(pose3D2DInfo.rect.x, pose3D2DInfo.rect.y, pose3D2DInfo.rect.width, pose3D2DInfo.rect.height);
        }
        return new Pose3D2DInfo(pose3D2DInfo.trackId, rect, point2fs, point3fs, pose3D2DInfo.scores2D, pose3D2DInfo.scores3D);
    }

    public static FaceInfo cloneFaceInfo(FaceInfo faceInfo){
        Point2f[] point2fs = null;
        if(faceInfo.landmarks != null) {
            point2fs = new Point2f[faceInfo.landmarks.length];
            for (int i = 0; i < faceInfo.landmarks.length; i++) {
                point2fs[i] = new Point2f(faceInfo.landmarks[i].x, faceInfo.landmarks[i].y);
            }
        }

        Point3f[] point3fs = null;
        if(faceInfo.landmarks3d != null) {
            point3fs = new Point3f[faceInfo.landmarks3d.length];
            for (int i = 0; i < faceInfo.landmarks3d.length; i++) {
                point3fs[i] = new Point3f(faceInfo.landmarks3d[i].x, faceInfo.landmarks3d[i].y, faceInfo.landmarks3d[i].z);
            }
        }

        Rect rect = null;
        if(faceInfo.rect != null) {
            rect = new Rect(faceInfo.rect.x, faceInfo.rect.y, faceInfo.rect.width, faceInfo.rect.height);
        }
        return new FaceInfo(rect, faceInfo.score, point2fs, point3fs);
    }


    public static HandInfo cloneHandInfo(HandInfo handInfo){
        Point2f[] point2fs = null;
        if(handInfo.landmarks != null) {
            point2fs = new Point2f[handInfo.landmarks.length];
            for (int i = 0; i < handInfo.landmarks.length; i++) {
                point2fs[i] = new Point2f(handInfo.landmarks[i].x, handInfo.landmarks[i].y);
            }
        }
        Rect rect = null;
        if(handInfo.rect != null) {
            rect = new Rect(handInfo.rect.x, handInfo.rect.y, handInfo.rect.width, handInfo.rect.height);
        }
        return new HandInfo(handInfo.handedness, point2fs, rect, handInfo.score, handInfo.type);
    }

    public static Hand3D2DInfo cloneHand3D2DInfo(Hand3D2DInfo hand3D2DInfo){
        Point2f[] point2fs = null;
        if(hand3D2DInfo.landmarks2D != null) {
            point2fs = new Point2f[hand3D2DInfo.landmarks2D.length];
            for (int i = 0; i < hand3D2DInfo.landmarks2D.length; i++) {
                point2fs[i] = new Point2f(hand3D2DInfo.landmarks2D[i].x, hand3D2DInfo.landmarks2D[i].y);
            }
        }

        Point3f[] point3fs = null;
        if(hand3D2DInfo.landmarks3D != null) {
            point3fs = new Point3f[hand3D2DInfo.landmarks3D.length];
            for (int i = 0; i < hand3D2DInfo.landmarks3D.length; i++) {
                point3fs[i] = new Point3f(hand3D2DInfo.landmarks3D[i].x, hand3D2DInfo.landmarks3D[i].y, hand3D2DInfo.landmarks3D[i].z);
            }
        }

        Rect rect = null;
        if(hand3D2DInfo.rect != null) {
            rect = new Rect(hand3D2DInfo.rect.x, hand3D2DInfo.rect.y, hand3D2DInfo.rect.width, hand3D2DInfo.rect.height);
        }
        return new Hand3D2DInfo(hand3D2DInfo.handedness, rect, point2fs, point3fs, hand3D2DInfo.scores2D, hand3D2DInfo.scores3D);
    }
}
