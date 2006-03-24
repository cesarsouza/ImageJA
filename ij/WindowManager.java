package ij;
import ij.plugin.Converter;
import ij.plugin.frame.Recorder;
import ij.macro.Interpreter;
import ij.text.TextWindow;
import ij.plugin.frame.PlugInFrame;
import java.awt.*;
import java.util.*;
import ij.gui.*;
import ij.desktop.gui.*;
import javax.swing.*;
import javax.swing.event.*;

/** This class consists of static methods used to manage ImageJ's windows. */
public class WindowManager {

	private static Vector imageList = new Vector();		 // list of image windows
	private static Vector nonImageList = new Vector();	 // list of non-image windows
	private static ImageWindow currentWindow;			 // active image window
	private static JInternalFrame frontWindow;
	private static ImagePlus tempCurrentImage;
        public static MDIDesktopPane theDesktop = new MDIDesktopPane();
	
	private WindowManager() {
            
	}
        
        public static void setDesktopBackground(){
            theDesktop.setBackground(new Color(102,100,165));
            //theDesktop.setPreferredSize(new Dimension(300,300));
        }
              

	/** Makes the specified image active. */
	public synchronized static void setCurrentWindow(ImageWindow win) {
		if (win==null || win.isClosed() || win.getImagePlus()==null) // deadlock-"wait to lock"
			return;
		setWindow(win);
		tempCurrentImage = null;
		if (win==currentWindow || imageList.size()==0)
			return;
		//IJ.log(win.getImagePlus().getTitle()+", previous="+(currentWindow!=null?currentWindow.getImagePlus().getTitle():"null") + ")");
		if (currentWindow!=null) {
			// free up pixel buffers AWT Image resources used by current window
			ImagePlus imp = currentWindow.getImagePlus();
			if (imp!=null && imp.lockSilently()) {
				imp.trimProcessor();
				Image img = imp.getImage();
				if (!Converter.newWindowCreated)
					imp.saveRoi();
				Converter.newWindowCreated = false;
				imp.unlock();
			}
		}
		Undo.reset();
		currentWindow = win;
		Menus.updateMenus();
	}
	
	/** Returns the active ImageWindow. */
	public static ImageWindow getCurrentWindow() {
		//if (IJ.debugMode) IJ.write("ImageWindow.getCurrentWindow");
		return currentWindow;
	}

	static int getCurrentIndex() {
		return imageList.indexOf(currentWindow);
	}

	/** Returns the active ImagePlus. */
	public synchronized static ImagePlus getCurrentImage() {
		//IJ.log("getCurrentImage: "+tempCurrentImage+"  "+currentWindow);
		if (tempCurrentImage!=null)
			return tempCurrentImage;
		else if (currentWindow!=null)
			return currentWindow.getImagePlus();
		else if (frontWindow!=null && (frontWindow instanceof ImageWindow))
			return ((ImageWindow)frontWindow).getImagePlus();
		else 	if (imageList.size()>0) {	
			ImageWindow win = (ImageWindow)imageList.elementAt(imageList.size()-1);
			return win.getImagePlus();
		} else
			return Interpreter.getLastBatchModeImage(); 
	}

	/** Returns the number of open image windows. */
	public static int getWindowCount() {
		int count = imageList.size();
		if (count==0 && tempCurrentImage!=null)
			count = 1;
		return count;
	}

	/** Returns the number of open images. */
	public static int getImageCount() {
		int count = imageList.size();
		count += Interpreter.getBatchModeImageCount();
		if (count==0 && tempCurrentImage!=null)
			count = 1;
		return count;
	}

	/** Returns the front most window or null. */
	public static JInternalFrame getFrontWindow() {
		return frontWindow;
	}

	/** Returns a list of the IDs of open images. Returns
		null if no windows are open. */
	public synchronized static int[] getIDList() {
		int nWindows = imageList.size();
		int[] batchModeImages = Interpreter.getBatchModeImageIDs();
		int nBatchImages = batchModeImages.length;
		if ((nWindows+nBatchImages)==0)
			return null;
		int[] list = new int[nWindows+nBatchImages];
		for (int i=0; i<nBatchImages; i++)
			list[i] = batchModeImages[i];
		int index = 0;
		for (int i=nBatchImages; i<nBatchImages+nWindows; i++) {
			ImageWindow win = (ImageWindow)imageList.elementAt(index++);
			list[i] = win.getImagePlus().getID();
		}
		return list;
	}

	/** Returns an array containing a list of the non-image windows. */
	synchronized static JInternalFrame[] getNonImageWindows() {
		JInternalFrame[] list = new JInternalFrame[nonImageList.size()];
		nonImageList.copyInto((JInternalFrame[])list);
		return list;
	}

	/** For IDs less than zero, returns the ImagePlus with the specified ID. 
		Returns null if no open window has a matching ID or no images are open. 
		For IDs greater than zero, returns the Nth ImagePlus. Returns null if 
		the ID is zero. */
	public synchronized static ImagePlus getImage(int imageID) {
		//if (IJ.debugMode) IJ.write("ImageWindow.getImage");
		if (imageID==0)
			return null;
		if (imageID<0) {
			ImagePlus imp2 = Interpreter.getBatchModeImage(imageID);
			if (imp2!=null) return imp2;
		}
		int nImages = imageList.size();
		if (nImages==0)
			return null;
		if (imageID>0) {
			if (imageID>nImages)
				return null;
			ImageWindow win = (ImageWindow)imageList.elementAt(imageID-1);
			if (win!=null)
				return win.getImagePlus();
			else
				return null;
		}
		ImagePlus imp = null;
		for (int i=0; i<imageList.size(); i++) {
			ImageWindow win = (ImageWindow)imageList.elementAt(i);
			ImagePlus imp2 = win.getImagePlus();
			if (imageID==imp2.getID()) {
				imp = imp2;
				break;
			}
		}
		return imp;
	}
	
	/** Returns the first image that has the specified title or null if it is not found. */
	public synchronized static ImagePlus getImage(String title) {
		int[] wList = getIDList();
		if (wList==null) return null;
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = getImage(wList[i]);
			if (imp!=null && imp.getTitle().equals(title))
				return imp;
		}
		return null;
	}

	/** Adds the specified window to the Window menu. */
	public synchronized static void addWindow(JInternalFrame win) {
		//IJ.write("addWindow: "+win.getTitle());
		if (win==null)
			return;
		else if (win instanceof ImageWindow) 
			addImageWindow((ImageWindow)win);
		else {
			Menus.insertWindowMenuItem(win);
			nonImageList.addElement(win);
                        theDesktop.add(win);
 		}
    }
        
        private static void addImageWindow(ImageWindow win) {
            imageList.addElement(win);
            theDesktop.add(win);
            Menus.addWindowMenuItem(win.getImagePlus());
            setCurrentWindow(win);
    }

	/** Removes the specified window from the Window menu. */
	public synchronized static void removeWindow(JInternalFrame win) {
		//IJ.write("removeWindow: "+win.getTitle());
		if (win instanceof ImageWindow)
			removeImageWindow((ImageWindow)win);
		else {
			int index = nonImageList.indexOf(win);
			ImageJ ij = IJ.getInstance();
			if (index>=0) {
			 	//if (ij!=null && !ij.quitting())
				Menus.removeWindowMenuItem(index);
				nonImageList.removeElement(win);
			}
		}
		setWindow(null);
	}

	private static void removeImageWindow(ImageWindow win) {
		int index = imageList.indexOf(win);
		if (index==-1)
			return;  // not on the window list
		if (imageList.size()>1) {
			int newIndex = index-1;
			if (newIndex<0)
				newIndex = imageList.size()-1;
			setCurrentWindow((ImageWindow)imageList.elementAt(newIndex));
		} else
			currentWindow = null;
		imageList.removeElementAt(index);
		int nonImageCount = nonImageList.size();
		if (nonImageCount>0)
			nonImageCount++;
		Menus.removeWindowMenuItem(nonImageCount+index);
		Menus.updateMenus();
		Undo.reset();
	}

	/** The specified frame becomes the front window, the one returnd by getFrontWindow(). */
	public static void setWindow(JInternalFrame win) {
		frontWindow = win;
		//IJ.log("Set window: "+(win!=null?win.getTitle():"null"));
    }

	/** Closes all windows. Stops and returns false if any image "save changes" dialog is canceled. */
	public synchronized static boolean closeAllWindows() {
		while (imageList.size()>0) {
			//ImagePlus imp = ((ImageWindow)imageList.elementAt(0)).getImagePlus();
			//IJ.write("Closing: " + imp.getTitle() + " " + imageList.size());
			if (!((ImageWindow)imageList.elementAt(0)).close())
				return false;
			IJ.wait(100);
		}
		JInternalFrame[] list = getNonImageWindows();
		for (int i=0; i<list.length; i++) {
			JInternalFrame frame = list[i];
			if (frame instanceof PlugInFrame)
				((PlugInFrame)frame).close();
			else if (frame instanceof TextWindow)
			((TextWindow)frame).close();
		}
		return true;
    }
    
	/** Activates the next window on the window list. */
	public static void putBehind() {
		if (IJ.debugMode) IJ.log("putBehind");
		if(imageList.size()<1 || currentWindow==null)
			return;
		int index = imageList.indexOf(currentWindow);
		index--;
		if (index<0)
			index = imageList.size()-1;
		ImageWindow win = (ImageWindow)imageList.elementAt(index);
		setCurrentWindow(win);
		win.toFront();
		Menus.updateMenus();
    }

	/** Makes the specified image temporarily the active image.
		Allows use of IJ.run() commands on images that
		are not displayed in a window. Call again with a null
		argument to revert to the previous active image. */
	public static void setTempCurrentImage(ImagePlus imp) {
		tempCurrentImage = imp;
    }
    
	/** Returns <code>tempCurrentImage</code>, which may be null. */
	public static ImagePlus getTempCurrentImage() {
		return tempCurrentImage;
	}

    /** Returns the frame with the specified title or null if a frame with that 
    	title is not found. */
    public static JInternalFrame getFrame(String title) {
		for (int i=0; i<nonImageList.size(); i++) {
			JInternalFrame frame = (JInternalFrame)nonImageList.elementAt(i);
			if (title.equals(frame.getTitle()))
				return frame;
		}
		int[] wList = getIDList();
		int len = wList!=null?wList.length:0;
		for (int i=0; i<len; i++) {
			ImagePlus imp = getImage(wList[i]);
			if (imp!=null) {
				if (imp.getTitle().equals(title))
					return (JInternalFrame)imp.getWindow();
			}
		}
		return null;
    }

	/** Activates a window selected from the Window menu. */
	synchronized static void activateWindow(String menuItemLabel, MenuItem item) {
		//IJ.write("activateWindow: "+menuItemLabel+" "+item);
		for (int i=0; i<nonImageList.size(); i++) {
			JInternalFrame win = (JInternalFrame)nonImageList.elementAt(i);
			String title = win.getTitle();
			if (menuItemLabel.equals(title)) {
				win.toFront();
				((CheckboxMenuItem)item).setState(false);
				if (Recorder.record)
					Recorder.record("selectWindow", title);
				return;
			}
		}
		int lastSpace = menuItemLabel.lastIndexOf(' ');
		if (lastSpace>0) // remove image size (e.g., " 90K")
			menuItemLabel = menuItemLabel.substring(0, lastSpace);
		for (int i=0; i<imageList.size(); i++) {
			ImageWindow win = (ImageWindow)imageList.elementAt(i);
			String title = win.getImagePlus().getTitle();
			if (menuItemLabel.equals(title)) {
				setCurrentWindow(win);
				win.toFront();
				int index = imageList.indexOf(win);
				int n = Menus.window.getItemCount();
				int start = Menus.WINDOW_MENU_ITEMS+Menus.windowMenuItems2;
				for (int j=start; j<n; j++) {
					MenuItem mi = Menus.window.getItem(j);
					((CheckboxMenuItem)mi).setState((j-start)==index);						
				}
				if (Recorder.record)
					Recorder.record("selectWindow", title);
				break;
			}
		}
    }
    
    /** Repaints all open image windows. */
    public synchronized static void repaintImageWindows() {
		int[] list = getIDList();
		if (list==null) return;
		for (int i=0; i<list.length; i++) {
			ImagePlus imp2 = getImage(list[i]);
			if (imp2!=null) {
				ImageWindow win = imp2.getWindow();
				if (win!=null) win.repaint();
			}
		}
	}
    
	static void showList() {
		if (IJ.debugMode) {
			for (int i=0; i<imageList.size(); i++) {
				ImageWindow win = (ImageWindow)imageList.elementAt(i);
				ImagePlus imp = win.getImagePlus();
				IJ.log(i + " " + imp.getTitle() + (win==currentWindow?"*":""));
			}
			IJ.log(" ");
		}
    }
    
}