package org.helioviewer.jhv.opengl.camera.newActions;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

import org.helioviewer.jhv.gui.IconBank;
import org.helioviewer.jhv.gui.IconBank.JHVIcon;
import org.helioviewer.jhv.gui.components.newComponents.MainFrame;
import org.helioviewer.jhv.opengl.camera.newCamera.CameraZoomAnimation;
import org.helioviewer.jhv.viewmodel.view.opengl.MainPanel;

public class ZoomInAction  extends AbstractAction {

    private static final long serialVersionUID = 1L;

    public ZoomInAction(boolean small) {
        super("Zoom in", small ? IconBank.getIcon(JHVIcon.ZOOM_IN_SMALL) : IconBank.getIcon(JHVIcon.ZOOM_IN));
        putValue(SHORT_DESCRIPTION, "Zoom in x2");
        putValue(MNEMONIC_KEY, KeyEvent.VK_I);
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.ALT_MASK));
    }

    public void actionPerformed(ActionEvent e) {
    	MainPanel compenentView = MainFrame.MAIN_PANEL;
    	
        double distance = -compenentView.getTranslation().z / 3;
        compenentView.addCameraAnimation(new CameraZoomAnimation(distance, 500));
    }

}
