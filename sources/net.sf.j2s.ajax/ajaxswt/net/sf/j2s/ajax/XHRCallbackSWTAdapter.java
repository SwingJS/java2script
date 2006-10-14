/*******************************************************************************
 * Java2Script Pacemaker (http://j2s.sourceforge.net)
 *
 * Copyright (c) 2006 ognize.com and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ognize.com - initial API and implementation
 *******************************************************************************/

package net.sf.j2s.ajax;

import net.sf.j2s.ajax.IXHRCallback;
import org.eclipse.swt.widgets.Display;


/**
 * This adapter class provides a default implementation of IXHRCallback.
 * This adapter class wraps current thread scope for those swtOnXXXX method. 
 * 
 * @author josson smith
 *
 * 2006-2-11
 */
public class XHRCallbackSWTAdapter implements IXHRCallback {
	
	/**
	 * Method will be called when the data transfer has been completed.
	 */
	public void swtOnLoaded() {
	}

	/**
	 * Method will be called immediately before receiving the message body 
	 * (if any). All HTTP headers have been received.
	 */
	public void swtOnReceiving() {
	}

	/**
	 * Method will be called when the user agent successfully acknowledged
	 * the request.
	 */
	public void swtOnSent() {
	}

	/**
	 * Method will be called when <code>XMLHttpRequestthe#open</code> method has been 
	 * successfully called.
	 */
	public void swtOnOpen() {
	}

	/**
	 * Call <code>#swtOnLoaded</code> when the data transfer has been completed.
	 * @j2sNative this.swtOnLoaded();
	 */
	public void onLoaded() {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				swtOnLoaded();
			}
		});
	}

	/**
	 * Call <code>#swtOnReceiving</code> immediately before receiving the 
	 * message body (if any). All HTTP headers have been received.
	 * @j2sNative this.swtOnReceiving();
	 */
	public void onReceiving() {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				swtOnReceiving();
			}
		});
	}

	/**
	 * Call <code>#swtOnSent</code> when the user agent successfully acknowledged
	 * the request.
	 * @j2sNative this.swtOnSent();
	 */
	public void onSent() {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				swtOnSent();
			}
		});
	}

	/**
	 * Call <code>#swtOnOpen</code> when <code>XMLHttpRequestthe#open</code> method has been 
	 * successfully called.
	 * @j2sNative this.swtOnOpen();
	 */
	public void onOpen() {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				swtOnOpen();
			}
		});
	}

}
