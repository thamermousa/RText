/*
 * 06/13/2009
 *
 * AssistanceIconPanel.java - A panel that sits alongside a text component,
 * that can display assistance icons for that component.
 * Copyright (C) 2009 Robert Futrell
 * robert_futrell at users.sourceforge.net
 * http://fifesoft.com
 *
 * This file is a part of RText.
 *
 * RText is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * RText is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.fife.ui.search;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ResourceBundle;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.text.JTextComponent;


/**
 * A panel meant to be displayed alongside a text component, that can display
 * assistance icons for that text component.
 *
 * @author Robert Futrell
 * @version 1.0
 */
class AssistanceIconPanel extends JPanel
						implements FocusListener, PropertyChangeListener {

	/**
	 * The width of this icon panel, to help align the components we're
	 * listening to with other combos or text fields without an
	 * AssistanceIconPanel.
	 */
	public static final int WIDTH		= 8;

	private JLabel iconLabel;
	private boolean showIcon;

	/**
	 * The tooltip text for the light bulb icon.  It is assumed that access
	 * to this field is single-threaded (on the EDT).
	 */
	private static String ASSISTANCE_AVAILABLE;


	/**
	 * Constructor.
	 *
	 * @param combo The combo box to listen to.
	 */
	public AssistanceIconPanel(JComponent comp) {

		setLayout(new BorderLayout());
		iconLabel = new JLabel(EmptyIcon.INSTANCE);
		iconLabel.setVerticalAlignment(SwingConstants.TOP);
		add(iconLabel, BorderLayout.NORTH);

		if (comp instanceof JComboBox) {
			JComboBox combo = (JComboBox)comp;
			Component c = combo.getEditor().getEditorComponent();
			if (c instanceof JTextComponent) { // Always true
				JTextComponent tc = (JTextComponent)c;
				tc.addFocusListener(this);
			}
		}
		else { // Usually a JTextComponent
			comp.addFocusListener(this);
		}

		comp.addPropertyChangeListener(
			ContentAssistable.ASSISTANCE_IMAGE, this);

	}


	/**
	 * Called when the combo box or text component gains focus.
	 *
	 * @param e The focus event.
	 */
	public void focusGained(FocusEvent e) {
		showIcon = true;
		iconLabel.repaint();
	}


	/**
	 * Called when the combo box or text component loses focus.
	 *
	 * @param e The focus event.
	 */
	public void focusLost(FocusEvent e) {
		showIcon = false;
		iconLabel.repaint();
	}


	/**
	 * Returns the "Content Assist Available" tooltip text for the light bulb
	 * icon.  It is assumed that this method is only called on the EDT.
	 *
	 * @return The text.
	 */
	static String getAssistanceAvailableText() {
		if (ASSISTANCE_AVAILABLE==null) {
			ResourceBundle msg = ResourceBundle.getBundle(
											"org.fife.ui.search.Search");
			ASSISTANCE_AVAILABLE = msg.getString("ContentAssistAvailable");
		}
		return ASSISTANCE_AVAILABLE;
	}


	/**
	 * Paints any child components.
	 *
	 * @param g The graphics context.
	 */
	protected void paintChildren(Graphics g) {
		if (showIcon) {
			super.paintChildren(g);
		}
	}


	/**
	 * Called when the property
	 * {@link ContentAssistable#ASSISTANCE_IMAGE} is fired by the component
	 * we are listening to.
	 *
	 * @param e The change event.
	 */
	public void propertyChange(PropertyChangeEvent e) {
		Image img = (Image)e.getNewValue();
		if (img==null && iconLabel.getIcon()!=EmptyIcon.INSTANCE) {
			iconLabel.setIcon(EmptyIcon.INSTANCE);
			iconLabel.setToolTipText(null);
		}
		else {
			iconLabel.setIcon(new ImageIcon(img));
			iconLabel.setToolTipText(getAssistanceAvailableText());
		}
	}


	/**
	 * An empty icon.
	 */
	private static class EmptyIcon implements Icon {

		private static final EmptyIcon INSTANCE = new EmptyIcon();

		public int getIconHeight() {
			return WIDTH;
		}

		public int getIconWidth() {
			return WIDTH;
		}

		public void paintIcon(Component c, Graphics g, int x, int y) {
		}
		
	}


}