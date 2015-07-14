package org.helioviewer.jhv.opengl.camera.newCamera;

import org.helioviewer.jhv.gui.GuiState3DWCS;
import org.helioviewer.jhv.opengl.camera.GL3DCamera;
import org.helioviewer.jhv.viewmodel.view.opengl.CompenentView;

/**
 * This animation zooms the camera by a given amount. Zooming only affects the
 * z-component of the {@link GL3DCamera}'s translation.
 * 
 * @author Simon Sp�rri (simon.spoerri@fhnw.ch)
 * 
 */
public class CameraZoomAnimation implements CameraAnimation {
    private boolean isFinished = false;

    private long startTime = -1;
    private long lastAnimationTime = -1;
    private long timeLeft = 0;

    private double distanceToTravel;
    private double distanceDelta;

    private double targetDistance;

    
    public CameraZoomAnimation(double distanceToTravel) {
        this(distanceToTravel, CameraAnimation.DEFAULT_ANIMATION_TIME);
    }

    public CameraZoomAnimation(double distanceToTravel, long duration) {
        this.distanceToTravel = distanceToTravel;
        this.timeLeft = duration;
        this.distanceDelta = distanceToTravel / this.timeLeft;
        GuiState3DWCS.mainComponentView.regristryAnimation(duration);
    }
    

    public void animate(CompenentView compenentView) {
    	System.out.println("startTime : " + startTime);
        if (this.startTime < 0) {
            this.startTime = System.currentTimeMillis();
            this.lastAnimationTime = System.currentTimeMillis();
            this.targetDistance = Math.max(CompenentView.MIN_DISTANCE, Math.min(CompenentView.MAX_DISTANCE, compenentView.getTranslation().z + this.distanceToTravel));
        }

        long timeDelta = System.currentTimeMillis() - lastAnimationTime;
        this.timeLeft -= timeDelta;
        if (timeLeft <= 0) {
        	compenentView.setZTranslation(targetDistance);
        } else {
            double zTranslation = Math.min(compenentView.getTranslation().z + this.distanceDelta * timeDelta, targetDistance);
            if (this.distanceToTravel < 0) {
                zTranslation = Math.max(compenentView.getTranslation().z + this.distanceDelta * timeDelta, targetDistance);
            }
        	compenentView.setZTranslation(zTranslation);
        }

        if (compenentView.getTranslation().z == this.targetDistance) {
            this.isFinished = true;
        }
        this.lastAnimationTime = System.currentTimeMillis();
    }

    public void updateWithAnimation(CameraAnimation animation) {
        if (animation instanceof CameraZoomAnimation) {
            CameraZoomAnimation ani = (CameraZoomAnimation) animation;
            this.timeLeft = Math.min(2000, this.timeLeft / 5 + ani.timeLeft);
            this.distanceToTravel += ani.distanceToTravel;
            this.targetDistance = Math.min(CompenentView.MIN_DISTANCE, Math.max(CompenentView.MAX_DISTANCE, this.targetDistance + ani.distanceToTravel));
            this.distanceDelta = this.distanceToTravel / this.timeLeft;
        }
    }

    public boolean isFinished() {
        return isFinished;
    }
}