package org.helioviewer.jhv.opengl.camera.actions;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.time.LocalDateTime;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

import org.helioviewer.jhv.JHVException.MetaDataException;
import org.helioviewer.jhv.gui.IconBank;
import org.helioviewer.jhv.gui.IconBank.JHVIcon;
import org.helioviewer.jhv.gui.MainFrame;
import org.helioviewer.jhv.gui.opengl.MainPanel;
import org.helioviewer.jhv.layers.AbstractImageLayer;
import org.helioviewer.jhv.layers.Layers;
import org.helioviewer.jhv.opengl.camera.animation.CameraZoomAnimation;
import org.helioviewer.jhv.viewmodel.TimeLine;

public class ZoomFitAction extends AbstractAction {

	private static final long serialVersionUID = 1L;

	public ZoomFitAction(boolean small) {
		super("Zoom to Fit", small ? IconBank.getIcon(JHVIcon.NEW_ZOOM_FIT, 16,
				16) : IconBank.getIcon(JHVIcon.NEW_ZOOM_FIT, 24, 24));
		putValue(SHORT_DESCRIPTION, "Zoom to Fit");
		putValue(MNEMONIC_KEY, KeyEvent.VK_F);
		putValue(ACCELERATOR_KEY,
				KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyEvent.ALT_MASK));
	}

	public void actionPerformed(ActionEvent e) {
		MainPanel compenentView = MainFrame.MAIN_PANEL;
		AbstractImageLayer activeLayer = Layers.getActiveImageLayer();
		if (activeLayer != null){
			Rectangle2D region;
			try {
				LocalDateTime currentDateTime = TimeLine.SINGLETON.getCurrentDateTime();
				region = activeLayer.getMetaData(currentDateTime).getPhysicalImageSize();
				if (region != null) {
					double halfWidth = region.getHeight() / 2;
					Dimension canvasSize = MainFrame.MAIN_PANEL.getSize();
					double aspect = canvasSize.getWidth()
							/ canvasSize.getHeight();
					halfWidth = aspect > 1 ? halfWidth * aspect : halfWidth;
					double halfFOVRad = Math.toRadians(MainPanel.FOV / 2.0);
					double distance = halfWidth
							* Math.sin(Math.PI / 2 - halfFOVRad)
							/ Math.sin(halfFOVRad);
					distance = distance - compenentView.getTranslation().z;
					compenentView.addCameraAnimation(new CameraZoomAnimation(
							distance, 500));
				}
			} catch (MetaDataException e1) {
				
				e1.printStackTrace();
			}
		} 
	}

}