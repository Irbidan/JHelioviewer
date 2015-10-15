package org.helioviewer.jhv.gui.actions;

import java.awt.event.ActionEvent;

import javax.annotation.Nullable;
import javax.swing.AbstractAction;

import org.helioviewer.jhv.gui.MainFrame;

public class SetCameraRotationInteractionAction extends AbstractAction
{
	public SetCameraRotationInteractionAction()
	{
		super("Rotate");
	}
	
	
	@Override
	public void actionPerformed(@Nullable ActionEvent e)
	{
		MainFrame.SINGLETON.MAIN_PANEL.activateRotationInteraction();
		MainFrame.SINGLETON.OVERVIEW_PANEL.activateRotationInteraction();
	}
}
