/*
 * Copyright (C) 2007-2011 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.zlibrary.core.application;

import java.util.*;

import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.view.ZLView;
import org.geometerplus.zlibrary.core.view.ZLViewWidget;

public abstract class ZLApplication {
	public static ZLApplication Instance() {
		return ourInstance;
	}

	private static ZLApplication ourInstance;

	public static final String NoAction = "none";

	private ZLApplicationWindow myWindow;
	private ZLView myView;

	private final HashMap<String,ZLAction> myIdToActionMap = new HashMap<String,ZLAction>();

	protected ZLApplication() {
		ourInstance = this;
	}

	protected final void setView(ZLView view) {
		if (view != null) {
			myView = view;
			final ZLViewWidget widget = getViewWidget();
			if (widget != null) {
				widget.reset();
				widget.repaint();
			}
			onViewChanged();
		}
	}

	public final ZLView getCurrentView() {
		return myView;
	}

	final void setWindow(ZLApplicationWindow window) {
		myWindow = window;
	}

	public void initWindow() {
		setView(myView);
	}

	public final ZLViewWidget getViewWidget() {
		return myWindow != null ? myWindow.getViewWidget() : null;
	}

	public final void onRepaintFinished() {
		if (myWindow != null) {
			myWindow.refreshMenu();
		}
		for (ButtonPanel panel : myPanels) {
			panel.updateStates();
		}
	}

	public final void onViewChanged() {
		hideAllPanels();
	}

	public final void addAction(String actionId, ZLAction action) {
		myIdToActionMap.put(actionId, action);
	}

	public final boolean isActionVisible(String actionId) {
		final ZLAction action = myIdToActionMap.get(actionId);
		return (action != null) && action.isVisible();
	}

	public final boolean isActionEnabled(String actionId) {
		final ZLAction action = myIdToActionMap.get(actionId);
		return (action != null) && action.isEnabled();
	}

	public final void doAction(String actionId) {
		final ZLAction action = myIdToActionMap.get(actionId);
		if (action != null) {
			action.checkAndRun();
		}
	}

	public final void doActionWithCoordinates(String actionId, int x, int y) {
		final ZLAction action = myIdToActionMap.get(actionId);
		if (action != null && action.isEnabled()) {
			action.runWithCoordinates(x, y);
		}
	}

	//may be protected
	abstract public ZLKeyBindings keyBindings();

	public final boolean hasActionForKey(String key, boolean longPress) {
		final String actionId = keyBindings().getBinding(key, longPress);
		return actionId != null && !NoAction.equals(actionId);	
	}

	public final boolean doActionByKey(String key, boolean longPress) {
		final String actionId = keyBindings().getBinding(key, longPress);
		if (actionId != null) {
			final ZLAction action = myIdToActionMap.get(actionId);
			return action != null && action.checkAndRun();
		}
		return false;
	}

	public void rotateScreen() {
		if (myWindow != null) {
			myWindow.rotate();
		}
	}

	public boolean canRotateScreen() {
		if (myWindow != null) {
			return myWindow.canRotate();
		}
		return false;
	}

	public boolean closeWindow() {
		onWindowClosing();
		if (myWindow != null) {
			myWindow.close();
		}
		return true;
	}

	public void onWindowClosing() {
	}

	public abstract void openFile(ZLFile file);

	//Action
	static abstract public class ZLAction {
		public boolean isVisible() {
			return true;
		}

		public boolean isEnabled() {
			return isVisible();
		}

		public final boolean checkAndRun() {
			if (isEnabled()) {
				run();
				return true;
			}
			return false;
		}

		abstract protected void run();

		protected void runWithCoordinates(int x, int y) {
			run();
		}
	}

	static public interface ButtonPanel {
		void updateStates();
		void hide();
	}
	private final List<ButtonPanel> myPanels = new LinkedList<ButtonPanel>();
	public final List<ButtonPanel> buttonPanels() {
		return Collections.unmodifiableList(myPanels);
	}
	public final void registerButtonPanel(ButtonPanel panel) {
		myPanels.add(panel);
	}
	public final void unregisterButtonPanel(ButtonPanel panel) {
		myPanels.remove(panel);
	}
	public final void hideAllPanels() {
		for (ButtonPanel panel : myPanels) {
			panel.hide();
		}
	}

	public int getBatteryLevel() {
		return (myWindow != null) ? myWindow.getBatteryLevel() : 0;
	}

	private Timer myTimer;
	private final HashMap<Runnable,Long> myTimerTaskPeriods = new HashMap<Runnable,Long>();
	private final HashMap<Runnable,TimerTask> myTimerTasks = new HashMap<Runnable,TimerTask>();
	private static class MyTimerTask extends TimerTask {
		private final Runnable myRunnable;

		MyTimerTask(Runnable runnable) {
			myRunnable = runnable;
		}

		public void run() {
			myRunnable.run();
		}
	}

	private void addTimerTaskInternal(Runnable runnable, long periodMilliseconds) {
		final TimerTask task = new MyTimerTask(runnable);
		myTimer.schedule(task, periodMilliseconds / 2, periodMilliseconds);
		myTimerTasks.put(runnable, task);
	}

	public final synchronized void startTimer() {
		if (myTimer == null) {
			myTimer = new Timer();
			for (Map.Entry<Runnable,Long> entry : myTimerTaskPeriods.entrySet()) {
				addTimerTaskInternal(entry.getKey(), entry.getValue());
			} 
		}
	}

	public final synchronized void stopTimer() {
		if (myTimer != null) {
			myTimer.cancel();
			myTimer = null;
			myTimerTasks.clear();
		}
	}

	public final synchronized void addTimerTask(Runnable runnable, long periodMilliseconds) {
		removeTimerTask(runnable);
		myTimerTaskPeriods.put(runnable, periodMilliseconds);
		if (myTimer != null) {
			addTimerTaskInternal(runnable, periodMilliseconds);
		}
	}	

	public final synchronized void removeTimerTask(Runnable runnable) {
		TimerTask task = myTimerTasks.get(runnable);
		if (task != null) {
			task.cancel();
			myTimerTasks.remove(runnable);
		}
		myTimerTaskPeriods.remove(runnable);
	}
}
