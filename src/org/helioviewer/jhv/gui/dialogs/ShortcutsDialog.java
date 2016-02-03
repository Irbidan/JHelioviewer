package org.helioviewer.jhv.gui.dialogs;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import org.helioviewer.jhv.base.Telemetry;
import org.helioviewer.jhv.gui.MainFrame;

/**
 * Dialog used to display basic usage tips for the program.
 * 
 * <p>
 * Basically, the dialog contains all shortcuts.
 */
public class ShortcutsDialog extends JDialog
{
	private final JButton closeButton = new JButton("Close");

	// private final JButton wikiButton = new JButton("JHelioviewer Wiki");
	// private final JButton jhvButton = new JButton("JHelioviewer.org");

	public ShortcutsDialog()
	{
		super(MainFrame.SINGLETON, "Shortcuts", true);

		Telemetry.trackEvent("Dialog", "Type", getClass().getSimpleName());

		setLayout(new BorderLayout());
		setResizable(false);

		final String sep = System.getProperty("line.separator");

		// the content panel:
		JTextArea shortcuts = new JTextArea("Keyboard shortcuts:                        Mouse shortcuts:" + sep + sep
				+ "ALT + c       Center active image          Scroll wheel up     Zoom in" + sep
				+ "ALT + t       Toggle fullscreen display    Scroll wheel down   Zoom out" + sep
				+ "ALT + i       Zoom in" + sep + "ALT + o       Zoom out" + sep + "ALT + k       Zoom to fit" + sep
				+ "ALT + l       Zoom to native resolution" + sep + "ALT + p       Play/pause movie" + sep
				+ "ALT + b       Step to previous frame" + sep + "ALT + n       Step to next frame" + sep
				+ "F1            Show shortcuts");
		shortcuts.setEditable(false);
		shortcuts.setFont(new Font("Courier", Font.PLAIN, 13));
		shortcuts.setBackground(getBackground());
		shortcuts.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
		add(shortcuts, BorderLayout.CENTER);

		// the buttons panel
		JPanel buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new GridLayout(0, 3));
		// buttonsPanel.add(wikiButton);
		// buttonsPanel.add(jhvButton);
		buttonsPanel.add(closeButton);
		buttonsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		add(buttonsPanel, BorderLayout.SOUTH);

		// set the action listeners for the buttons
		closeButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(@Nullable ActionEvent e)
			{
				dispose();
			}
		});
		// wikiButton.addActionListener(this);
		// jhvButton.addActionListener(this);

		pack();
		setSize(getPreferredSize().width, getPreferredSize().height);
		setLocationRelativeTo(MainFrame.SINGLETON);

		DialogTools.setDefaultButtons(closeButton, closeButton);

		setVisible(true);
	}
}