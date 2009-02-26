/*******************************************************************************
 * Copyright (c) 2007 java2script.org and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Zhou Renjian - initial API and implementation
 *******************************************************************************/
package net.sf.j2s.ajax;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.sf.j2s.ajax.HttpRequest;
import net.sf.j2s.ajax.SimpleRPCRequest;
import net.sf.j2s.ajax.SimpleSerializable;
import net.sf.j2s.ajax.XHRCallbackAdapter;

/**
 * 
 * @author zhou renjian
 * 
 * @j2sSuffix
 * window["$p1p3p$"] = net.sf.j2s.ajax.SimplePipeRequest.parseReceived;
 * window["$p1p3b$"] = net.sf.j2s.ajax.SimplePipeRequest.pipeNotifyCallBack;
 */
public class SimplePipeRequest extends SimpleRPCRequest {
	
	/**
	 * Status of pipe: ok.
	 */
	protected static final String PIPE_STATUS_OK = "o"; // "ok";

	/**
	 * Status of pipe: destroyed.
	 */
	protected static final String PIPE_STATUS_DESTROYED = "d"; // "destroyed";
	
	/**
	 * Status of pipe: lost.
	 */
	protected static final String PIPE_STATUS_LOST = "l"; // "lost";

	
	/**
	 * Type of pipe request: query
	 */
	protected static final String PIPE_TYPE_QUERY = "q"; // "query";
	
	/**
	 * Type of pipe request: subdomain-query
	 */
	protected static final String PIPE_TYPE_SUBDOMAIN_QUERY = "u"; // "subdomain-query";
	
	/**
	 * Type of pipe request: notify
	 */
	protected static final String PIPE_TYPE_NOTIFY = "n"; // "notify";
	
	/**
	 * Type of pipe request: script
	 */
	protected static final String PIPE_TYPE_SCRIPT = "s"; // "script";
	
	/**
	 * Type of pipe request: xss
	 */
	protected static final String PIPE_TYPE_XSS = "x"; // "xss";
	
	/**
	 * Type of pipe request: continuum
	 */
	protected static final String PIPE_TYPE_CONTINUUM = "c"; // "continuum";
	
	
	/**
	 * Query key for pipe: pipekey
	 */
	protected static final String FORM_PIPE_KEY = "k"; // "pipekey";
	
	/**
	 * Query key for pipe: pipetype
	 */
	protected static final String FORM_PIPE_TYPE = "t"; // "pipetype";
	
	/**
	 * Query key for pipe: pipetype
	 */
	protected static final String FORM_PIPE_DOMAIN = "d"; // "pipedomain";

	/**
	 * Query key for pipe: pipernd
	 */
	protected static final String FORM_PIPE_RANDOM = "r"; // "pipernd";
	
	static final int PIPE_KEY_LENGTH = 6;

	public static final int MODE_PIPE_QUERY = 3;
	
	public static final int MODE_PIPE_CONTINUUM = 4;
	
	private static int pipeMode = MODE_PIPE_CONTINUUM;
	
	private static long pipeQueryInterval = 1000;
	
	static long pipeLiveNotifyInterval = 25000;
	
	private static long reqCount = 0;
	
	public static int getPipeMode() {
		return pipeMode;
	}
	
	public static long getQueryInterval() {
		return pipeQueryInterval;
	}
	
	public static void switchToQueryMode() {
		pipeMode = MODE_PIPE_QUERY;
		pipeQueryInterval = 1000;
	}
	
	public static void switchToQueryMode(long ms) {
		pipeMode = MODE_PIPE_QUERY;
		if (ms < 0) {
			ms = 1000;
		}
		pipeQueryInterval = ms;
	}
	
	public static void switchToContinuumMode() {
		pipeMode = MODE_PIPE_CONTINUUM;
	}
	
	/**
	 * Construct request string for pipe.
	 * @param pipeKey
	 * @param pipeRequestType
	 * @param rand
	 * @return request data for both GET and POST request. 
	 */
	protected static String constructRequest(String pipeKey, String pipeRequestType, boolean rand) {
		reqCount++;
		return FORM_PIPE_KEY + "=" + pipeKey + "&" 
				+ FORM_PIPE_TYPE + "=" + pipeRequestType 
				+ (rand ? "&" + FORM_PIPE_RANDOM + "=" + reqCount : "");
	}
	
	protected static void sendRequest(HttpRequest request, String method, String url, 
			String data, boolean async) {
		if ("GET".equals(method.toUpperCase())) {
			request.open(method, url + (url.indexOf('?') != -1 ? "&" : "?") + data, async);
			request.send(null);
		} else {
			request.open(method, url, async);
			request.send(data);
		}
	}
	
	/**
	 * 
	 * @param runnable
	 * 
	 * @j2sNative
	 * runnable.ajaxIn ();
	 * net.sf.j2s.ajax.SimplePipeRequest.pipeRequest(runnable);
	 */
	public static void pipe(final SimplePipeRunnable runnable) {
		runnable.ajaxIn();
		if (getRequstMode() == MODE_LOCAL_JAVA_THREAD) {
			new Thread(new Runnable() {
				public void run() {
					try {
						runnable.ajaxRun();
					} catch (RuntimeException e) {
						e.printStackTrace(); // should never fail in Java thread mode!
						runnable.ajaxFail();
						return;
					}
					keepPipeLive(runnable);
					runnable.ajaxOut();
				}
			}, "Pipe Request Thread").start();
		} else {
			pipeRequest(runnable);
		}
	}

	/**
	 * Be used in Java mode to keep the pipe live.
	 * 
	 * @param runnable
	 * 
	 * @j2sIgnore
	 */
	static void keepPipeLive(final SimplePipeRunnable runnable) {
		new Thread(new Runnable() {
			
			public void run() {
				do {
					long interval = pipeLiveNotifyInterval;
					
					while (interval > 0) {
						try {
							Thread.sleep(Math.min(interval, 1000));
							interval -= 1000;
						} catch (InterruptedException e) {
							//e.printStackTrace();
						}
						if (getRequstMode() == MODE_LOCAL_JAVA_THREAD) {
							if (!runnable.isPipeLive()) {
								break;
							}
						} else {
							SimplePipeRunnable pipeRunnable = SimplePipeHelper.getPipe(runnable.pipeKey);
							if (pipeRunnable == null || !pipeRunnable.isPipeLive()) {
								break;
							}
						}
					}
					
					if (getRequstMode() == MODE_LOCAL_JAVA_THREAD) {
						boolean pipeLive = runnable.isPipeLive();
						if (pipeLive) {
							runnable.keepPipeLive();
						} else {
							boolean okToClose = SimplePipeHelper.waitAMomentForClosing(runnable);
							if (okToClose) {
								runnable.pipeDestroy(); // Pipe's server side destroying
								runnable.pipeClosed(); // Pipe's client side closing
								break;
							}
						}
					} else {
						SimplePipeRunnable r = SimplePipeHelper.getPipe(runnable.pipeKey);
						if (r != null) {
							HttpRequest request = new HttpRequest();
							String pipeKey = runnable.pipeKey;
							String pipeMethod = runnable.getPipeMethod();
							String pipeURL = runnable.getPipeURL();

							String pipeRequestData = constructRequest(pipeKey, PIPE_TYPE_NOTIFY, false);
							sendRequest(request, pipeMethod, pipeURL, pipeRequestData, false);
							String response = request.getResponseText();
							if (response != null && response.indexOf("\"" + PIPE_STATUS_LOST + "\"") != -1) {
								runnable.pipeAlive = false;
								runnable.pipeLost();
								SimplePipeHelper.removePipe(pipeKey);
								// may need to inform user that connection is already lost!
								break;
							}
						} else {
							break;
						}
					}
				} while (true);
			}
		
		}, "Pipe Live Notifier Thread").start();
	}

	private static void pipeRequest(final SimplePipeRunnable runnable) {
		String url = runnable.getHttpURL();
		String method = runnable.getHttpMethod();
		String serialize = runnable.serialize();
		if (method == null) {
			method = "POST";
		}
		Object ajaxOut = null;
		/**
		 * Need to call #ajaxPipe inside #checkXSS
		 * 
		 * @j2sNative
		 * ajaxOut = runnable.ajaxOut;
		 * runnable.ajaxOut = (function (aO, r) {
		 * 	return function () {
		 * 		aO.apply (r, []);
		 * 		net.sf.j2s.ajax.SimplePipeRequest.ajaxPipe (r);
		 * 	};
		 * }) (ajaxOut, runnable); 
		 */ { if (ajaxOut == null) ajaxOut = null; /* no warning */ }
		if (checkXSS(url, serialize, runnable)) {
			// Already send out pipe request in XSS mode. Just return here.
			return;
		}
		/**
		 * @j2sNative
		 * runnable.ajaxOut = ajaxOut;
		 */ {}
		String url2 = SimpleRPCRequest.adjustRequestURL(method, url, serialize);
		if (url2 != url) {
			serialize = null;
		}
		final HttpRequest request = new HttpRequest();
		request.open(method, url, true);
		request.registerOnReadyStateChange(new XHRCallbackAdapter() {
			public void onLoaded() {
				String responseText = request.getResponseText();
				if (responseText == null || responseText.length() == 0) {
					runnable.ajaxFail(); // should seldom fail!
					return;
				}
				runnable.deserialize(responseText);
				
				runnable.ajaxOut();
				
				ajaxPipe(runnable);
			}
		});
		request.send(serialize);
	}

	/**
	 * Load or send data for pipe using SCRIPT tag.
	 * 
	 * @param url
	 * 
	 * @j2sNative
var script = document.createElement ("SCRIPT");
script.type = "text/javascript";
script.src = url;
var iframeID = arguments[1];
var userAgent = navigator.userAgent.toLowerCase ();
var isOpera = (userAgent.indexOf ("opera") != -1);
var isIE = (userAgent.indexOf ("msie") != -1) && !isOpera;
if (typeof (script.onreadystatechange) == "undefined" || !isIE) { // W3C
	script.onerror = function () {
		this.onerror = null;
		if (iframeID != null) {
			if (window.parent == null || window.parent["net"] == null) return;
			window.parent.net.sf.j2s.ajax.SimplePipeRequest.lastQueryReceived = true;
			document.getElementsByTagName ("HEAD")[0].removeChild (this);
			var iframe = window.parent.document.getElementById (iframeID);
			if (iframe != null) {
				iframe.parentNode.removeChild (iframe);
			}
			return;
		}
		if (window == null || window["net"] == null) return;
		net.sf.j2s.ajax.SimplePipeRequest.lastQueryReceived = true; 
		document.getElementsByTagName ("HEAD")[0].removeChild (this);
	};
	script.onload = function () {
		this.onload = null;
		if (iframeID != null) {
			if (window.parent == null || window.parent["net"] == null) return;
			window.parent.net.sf.j2s.ajax.SimplePipeRequest.lastQueryReceived = true;
			document.getElementsByTagName ("HEAD")[0].removeChild (this);
			var iframe = window.parent.document.getElementById (iframeID);
			if (iframe != null) {
				iframe.parentNode.removeChild (iframe);
			}
			return;
		}
		if (window == null || window["net"] == null) return;
		net.sf.j2s.ajax.SimplePipeRequest.lastQueryReceived = true; 
		document.getElementsByTagName ("HEAD")[0].removeChild (this);
	};
} else { // IE
	script.defer = true;
	script.onreadystatechange = function () {
		var state = "" + this.readyState;
		if (state == "loaded" || state == "complete") {
			this.onreadystatechange = null; 
			if (iframeID != null) {
				if (window.parent == null || window.parent["net"] == null) return;
				window.parent.net.sf.j2s.ajax.SimplePipeRequest.lastQueryReceived = true;
				document.getElementsByTagName ("HEAD")[0].removeChild (this);
				var iframe = window.parent.document.getElementById (iframeID);
				if (iframe != null) {
					iframe.parentNode.removeChild (iframe);
				}
				return;
			}
			if (window == null || window["net"] == null) return;
			net.sf.j2s.ajax.SimplePipeRequest.lastQueryReceived = true; 
			document.getElementsByTagName ("HEAD")[0].removeChild (this);
		}
	};
}
var head = document.getElementsByTagName ("HEAD")[0];
head.appendChild (script);
	 */
	static void loadPipeScript(String url) {
		// only for JavaScript
	}

	/**
	 * Load or send data for pipe using SCRIPT tag.
	 * 
	 * @param url
	 * 
	 * @j2sNative
var iframe = document.createElement ("IFRAME");
iframe.style.display = "none";
var iframeID = null;
do {
	iframeID = "pipe-script-" + Math.round (10000000 * Math.random ());
} while (document.getElementById (iframeID) != null);
iframe.id = iframeID;
document.body.appendChild (iframe);
var html = "<html><head><title></title>";
html += "<script type=\"text/javascript\">\r\n";
html += "window[\"$p1p3p$\"] = function (string) {\r\n";
html += "		with (window.parent) {\r\n";
html += "				net.sf.j2s.ajax.SimplePipeRequest.parseReceived (string);\r\n";
html += "		};\r\n";
html += "};\r\n";
html += "window[\"$p1p3b$\"] = function (key, result) {\r\n";
html += "		with (window.parent) {\r\n";
html += "				net.sf.j2s.ajax.SimplePipeRequest.pipeNotifyCallBack (key, result);\r\n";
html += "		};\r\n";
html += "};\r\n";
html += "</scr" + "ipt></head><body><script type=\"text/javascript\">\r\n";
if (ClassLoader.isOpera)
html += "window.setTimeout (function () {\r\n";
html += "(" + net.sf.j2s.ajax.SimplePipeRequest.loadPipeScript + ") (";
html += "\"" + url.replace (/"/g, "\\\"") + "\", \"" + iframeID + "\"";
html += ");\r\n";
if (ClassLoader.isOpera)
html += "}, " + (net.sf.j2s.ajax.SimplePipeRequest.pipeQueryInterval >> 2) + ");\r\n";
html += "</scr" + "ipt></body></html>";
net.sf.j2s.ajax.SimplePipeRequest.iframeDocumentWrite (iframe, html);
	 */
	static void loadPipeIFrameScript(String url) {
		// only for JavaScript
		iframeDocumentWrite(null, null);
	}
	/**
	 * @param handle
	 * @param html
	 * @j2sNative
	var handle = arguments[0];
	var html = arguments[1];
	if (handle.contentWindow != null) {
		handle.contentWindow.location = "about:blank";
	} else { // Opera
		handle.src = "about:blank";
	}
	try {
		var doc = handle.contentWindow.document;
		doc.open ();
		doc.write (html);
		doc.close ();
		// To avoid blank title in title bar
		document.title = document.title;
	} catch (e) {
		window.setTimeout ((function (handle, html) {
			return function () {
				var doc = handle.contentWindow.document;
				doc.open ();
				doc.write (html);
				doc.close ();
				// To avoid blank title in title bar
				document.title = document.title;
			};
		}) (handle, html), 25);
	}
	 */
	private native static void iframeDocumentWrite(Object handle, String html);
	
	static void pipeScript(SimplePipeRunnable runnable) { // xss
		String url = runnable.getPipeURL();
		String requestURL = url + (url.indexOf('?') != -1 ? "&" : "?")
				+ constructRequest(runnable.pipeKey, PIPE_TYPE_XSS, true);
		if (isXSSMode(url)) {
			// in xss mode, iframe is used to avoid blocking other *.js loading 
			loadPipeIFrameScript(requestURL);
			return;
		}
		loadPipeScript(requestURL);
		// only for JavaScript
	}
	
	/**
	 * @param runnable
	 * @param domain
	 * @j2sNative
var ifr = document.createElement ("IFRAME");
ifr.style.display = "none";
var pipeKey = runnable.pipeKey;
var spr = net.sf.j2s.ajax.SimplePipeRequest;
var url = runnable.getPipeURL();
ifr.src = url + (url.indexOf('?') != -1 ? "&" : "?") 
		+ spr.constructRequest(pipeKey, spr.PIPE_TYPE_SUBDOMAIN_QUERY, true)
		+ "&" + spr.FORM_PIPE_DOMAIN + "=" + domain;
document.body.appendChild (ifr);
	 */
	static void pipeSubdomainQuery(SimplePipeRunnable runnable, String domain) {
		// only for JavaScript
	}
	
	static void pipeNotify(SimplePipeRunnable runnable) { // notifier
		String url = runnable.getPipeURL();
		loadPipeScript(url + (url.indexOf('?') != -1 ? "&" : "?")
				+ constructRequest(runnable.pipeKey, PIPE_TYPE_NOTIFY, true));
		// only for JavaScript
	}
	
	static void pipeNotifyCallBack(String key, String result) {
		//System.out.println(key + "::" + result);
		if (PIPE_STATUS_LOST.equals(result)) {
			SimplePipeRunnable pipe = SimplePipeHelper.getPipe(key);
			if (pipe != null) {
				pipe.pipeAlive = false;
				pipe.pipeLost();
				SimplePipeHelper.removePipe(key);
			}
		}
		// only for JavaScript
	}
	
	/**
	 * Each query will tell that the pipe is still alive.
	 * 
	 * @param runnable
	 */
	static void pipeQuery(SimplePipeRunnable runnable) {
		final HttpRequest pipeRequest = new HttpRequest();
		String pipeKey = runnable.pipeKey;
		String pipeMethod = runnable.getPipeMethod();
		String pipeURL = runnable.getPipeURL();
		
		pipeRequest.registerOnReadyStateChange(new XHRCallbackAdapter() {
		
			@Override
			public void onLoaded() {
				/**
				 * Maybe user click on refresh button!
				 * @j2sNative
				 * if (window == null || window["net"] == null) return;
				 */ {}
				onPipeDataReceived(pipeRequest.getResponseText());
				/**
				 * @j2sNative
				 * net.sf.j2s.ajax.SimplePipeRequest.lastQueryReceived = true;
				 */ {}
			}
		
		});

		boolean async = false;
		/**
		 * In JavaScript such queries are not wrapped inside Thread, so asynchronous mode is required!
		 * @j2sNative
		 * async = true;
		 */ {}
		String pipeRequestData = constructRequest(pipeKey, PIPE_TYPE_QUERY, true);
		sendRequest(pipeRequest, pipeMethod, pipeURL, pipeRequestData, async);
	}

	static void onPipeDataReceived(String responseText) {
		if (responseText != null) {
			String retStr = parseReceived(responseText);
			if (retStr != null && retStr.length() > 0) {
				String destroyedKey = PIPE_STATUS_DESTROYED;
				if (retStr.indexOf(destroyedKey) == 0) {
					int beginIndex = destroyedKey.length() + 1;
					String pipeKeyStr = retStr.substring(beginIndex, beginIndex + PIPE_KEY_LENGTH);
					SimplePipeRunnable pipe = SimplePipeHelper.getPipe(pipeKeyStr);
					if (pipe != null) {
						pipe.pipeAlive = false;
						pipe.pipeClosed();
						SimplePipeHelper.removePipe(pipeKeyStr);
					}
					//SimplePipeHelper.removePipe(pipeKeyStr);
				}
			}
		}
	}

	/**
	 * Create pipe connection for the SimplePipeRunnable.
	 * In Java, customized HttpRequest object is used to create Comet connection.
	 * In JavaScript, IFRAME is used to simulate Comet connection.
	 * In both mode, it is necessary to notify the server that pipe's client end is still
	 * alive in periods.
	 * To send out the notification, Java mode just send out request by HttpRequest, JavaScript
	 * mode will try to send notification requests in XMLHttpRequest or SCRIPT mode according
	 * to the scenarios.
	 * 
	 * @param runnable
	 * 
	 * @j2sNative
var ifr = document.createElement ("IFRAME");
ifr.style.display = "none";
var pipeKey = runnable.pipeKey;
var spr = net.sf.j2s.ajax.SimplePipeRequest;
var url = runnable.getPipeURL();
var subdomain = arguments[1];
ifr.src = url + (url.indexOf('?') != -1 ? "&" : "?") 
		+ spr.constructRequest(pipeKey, spr.PIPE_TYPE_SCRIPT, true)
		+ (subdomain == null ? ""
				: "&" + spr.FORM_PIPE_DOMAIN + "=" + subdomain);
document.body.appendChild (ifr);
var threadFun = function (pipeFun, key) {
		return function () {
			var runnable = net.sf.j2s.ajax.SimplePipeHelper.getPipe(key);
			if (runnable != null) {
				var spr = net.sf.j2s.ajax.SimplePipeRequest;
				//if (spr.lastQueryReceived) {
					pipeFun (runnable);
					//spr.lastQueryReceived = false;
				//}
				window.setTimeout (arguments.callee, spr.pipeLiveNotifyInterval);
			}
		};
};
//spr.lastQueryReceived = true;
var fun = threadFun (spr.pipeNotify, runnable.pipeKey);
window.setTimeout (fun, spr.pipeLiveNotifyInterval);
	 */
	static void pipeContinuum(final SimplePipeRunnable runnable) {
		HttpRequest pipeRequest = new HttpRequest() {
			
			/*
			 * A hack to make HttpRequest to support receiving and parsing data
			 * in a Comet way.
			 * 
			 * (non-Javadoc)
			 * @see net.sf.j2s.ajax.HttpRequest#initializeReceivingMonitor()
			 */
			@Override
			protected IXHRReceiving initializeReceivingMonitor() {
				return new HttpRequest.IXHRReceiving() {
					public boolean receiving(ByteArrayOutputStream baos, byte b[], int off, int len) {
						baos.write(b, off, len);
						/*
						 * It is OK to convert to string, because SimpleSerialize's
						 * serialized string contains only ASCII chars.
						 */
						String string = baos.toString();
						String resetString = parseReceived(string);
						if (resetString != null) {
							String destroyedKey = PIPE_STATUS_DESTROYED;
							if (resetString.indexOf(destroyedKey) == 0) {
								int beginIndex = destroyedKey.length() + 1;
								// Following 32 is the length of pipe key string
								String pipeKeyStr = resetString.substring(beginIndex, beginIndex + PIPE_KEY_LENGTH);
								SimplePipeRunnable pipe = SimplePipeHelper.getPipe(pipeKeyStr);
								if (pipe != null) {
									pipe.pipeAlive = false;
									pipe.pipeClosed();
									SimplePipeHelper.removePipe(pipeKeyStr);
								}
								resetString = resetString.substring(beginIndex + PIPE_KEY_LENGTH + 1);
							}
							baos.reset();
							try {
								baos.write(resetString.getBytes());
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						return true;
					}
				
				};
			}
		
		};
		
		pipeRequest.registerOnReadyStateChange(new XHRCallbackAdapter() {
		
			@Override
			public void onReceiving() {
				keepPipeLive(runnable);
			}

			@Override
			public void onLoaded() { // on case that no destroy event is sent to client
				if (SimplePipeHelper.getPipe(runnable.pipeKey) != null) {
					runnable.pipeClosed();
					SimplePipeHelper.removePipe(runnable.pipeKey);
				}
			}
		
		});
		pipeRequest.setCometConnection(true);

		String pipeKey = runnable.pipeKey;
		String pipeMethod = runnable.getPipeMethod();
		String pipeURL = runnable.getPipeURL();

		String pipeRequestData = constructRequest(pipeKey, PIPE_TYPE_SCRIPT, false);
		sendRequest(pipeRequest, pipeMethod, pipeURL, pipeRequestData, true);
	}
	
	/**
	 * Parse a SimpleSerializable object's string from given string.
	 * 
	 * The given string should be in format of "X...WLL101...#...$...". The first
	 * 32-length string is pipe key string. And the following string is the
	 * serialized string of SimpleSerializable object or "pipe-is-destroyed",
	 * which indicates that pipe is destroyed.
	 * 
	 * If given string is not in the above format, it is considered that the
	 * string is not completed yet. And in this scenario, return null to
	 * indicate to keep receiving more data before call this method again.
	 * 
	 * If given string contains above format string fragment, the segment
	 * will be parsed and relative {@link SimplePipeRunnable#deal(SimpleSerializable)}
	 * will be called on the string fragment. And the rest string is returned
	 * so later data may continue to construct a new string. 
	 * 
	 * @param string
	 * @return null if given string is not completed, or rest of string after
	 * being parsed.
	 */
	public static String parseReceived(final String string) {
		//System.out.println(string);
		SimpleSerializable ss = null;
		int start = 0;
		while (string.length() > start + PIPE_KEY_LENGTH) { // should be bigger than 48 ( 32 + 6 + 1 + 8 + 1)
			String destroyedKey = PIPE_STATUS_DESTROYED;
			int end = start + PIPE_KEY_LENGTH;
			if (destroyedKey.equals(string.substring(end,
					end + destroyedKey.length()))) {
				/**
				 * @j2sNative
				 * var key = string.substring(start, end);
				 * var pipe = net.sf.j2s.ajax.SimplePipeHelper.getPipe(key)
				 * if (pipe != null) {
				 * 	pipe.pipeAlive = false;
				 * 	pipe.pipeClosed();
				 * }
				 * net.sf.j2s.ajax.SimplePipeHelper.removePipe(key);
				 */ {}
				return destroyedKey + ":" + string.substring(start, end) 
						+ ":" + string.substring(end + destroyedKey.length());
			}
			if ((ss = SimpleSerializable.parseInstance(string, end)) == null
					|| !ss.deserialize(string, end)) {
				break;
			}
			String key = string.substring(start, end);
			SimplePipeRunnable runnable = SimplePipeHelper.getPipe(key);
			if (runnable != null) { // should always satisfy this condition
				runnable.deal(ss);
			}
			
			start = restStringIndex(string, start);
		}
		if (start != 0) {
			return string.substring(start);
		}
		return string;
	}

	/**
	 * Return the string index from beginning of next SimpleSerializable
	 * instance.
	 * 
	 * @param string
	 * @return
	 */
	static int restStringIndex(final String string, int start) {
		// Format: WLL101ClassName#NNNNNN$SerializedData...
		int idx1 = string.indexOf('#', start) + 1;
		int idx2 = string.indexOf('$', idx1);
		String sizeStr = string.substring(idx1, idx2);
		sizeStr = sizeStr.replaceFirst("^0+", "");
		int size = 0;
		if (sizeStr.length() != 0) {
			try {
				size = Integer.parseInt(sizeStr);
			} catch (NumberFormatException e) {
				//
			}
		}
		int end = idx2 + size + 1;
		if (end <= string.length()) {
			//string = string.substring(end);
			return end;
		} else {
			return start;
		}
		//return string;
	}
	
	/**
	 * Start pipe in AJAX mode.
	 * In Java mode, thread is used to receive data from pipe.
	 * In JavaScript mode, IFRAME or XHR/SCRIPT requests are used to receive data 
	 * from server.
	 * 
	 * @param runnable
	 */
	static void ajaxPipe(final SimplePipeRunnable runnable) {
		SimplePipeHelper.registerPipe(runnable.pipeKey, runnable);
		
		/*
		 * Here in JavaScript mode, try to detect whether it's in cross site
		 * script mode or not. In XSS mode, <SCRIPT> is used to make requests. 
		 */
		String pipeURL = runnable.getPipeURL();
		boolean isXSS = isXSSMode(pipeURL);
		boolean isSubdomain = false;
		if (isXSS) {
			isSubdomain = isSubdomain(pipeURL);
		}

		if ((!isXSS || isSubdomain) && pipeMode == MODE_PIPE_CONTINUUM)
			/**
			 * @j2sNative
			 * var subdomain = null;
			 * if (isSubdomain) {
			 * 	subdomain = window.location.host;
			 * 	if (subdomain != null) {
			 * 		var idx = subdomain.indexOf (":");
			 * 		if (idx != -1) {
			 * 			subdomain = subdomain.substring (0, idx);
			 * 		}
			 * 		document.domain = subdomain; // set owner iframe's domain
			 * 	}
			 * }
			 * net.sf.j2s.ajax.SimplePipeRequest.pipeContinuum (runnable, subdomain);
			 */
		{
			//pipeQuery(runnable, "continuum");
			new Thread(new Runnable(){
				public void run() {
					pipeContinuum(runnable);
				}
			}).start();
		} else
			/**
			 * @j2sNative
if (isXSS && isSubdomain
 		&& net.sf.j2s.ajax.SimplePipeRequest.isSubdomainXSSSupported ()) {
	var subdomain = null;
	subdomain = window.location.host;
	if (subdomain != null) {
		var idx = subdomain.indexOf (":");
		if (idx != -1) {
			subdomain = subdomain.substring (0, idx);
		}
		document.domain = subdomain; // set owner iframe's domain
	}
	net.sf.j2s.ajax.SimplePipeRequest.pipeSubdomainQuery (runnable, subdomain);
	return;
}
var spr = net.sf.j2s.ajax.SimplePipeRequest;
spr.lastQueryReceived = true;
if (spr.queryTimeoutHandle != null) {
	window.clearTimeout (spr.queryTimeoutHandle);
	spr.queryTimeoutHandle = null;
}
(function (pipeFun, key) { // Function that simulate a thread
	return function () {
		var runnable = net.sf.j2s.ajax.SimplePipeHelper.getPipe(key);
		if (runnable != null) {
			var spr = net.sf.j2s.ajax.SimplePipeRequest;
			if (spr.lastQueryReceived) {
				spr.lastQueryReceived = false;
				pipeFun (runnable);
			}
			spr.queryTimeoutHandle = window.setTimeout (arguments.callee, spr.pipeQueryInterval);
		}
	};
}) ((!isXSS) ? spr.pipeQuery : spr.pipeScript, runnable.pipeKey) ()
			 */
		{
			final String key = runnable.pipeKey;
			new Thread(new Runnable() {
				public void run() {
					SimplePipeRunnable runnable = null;
					while ((runnable = SimplePipeHelper.getPipe(key)) != null) {
						pipeQuery(runnable);
						try {
							Thread.sleep(pipeQueryInterval);
						} catch (InterruptedException e) {
							//e.printStackTrace();
						}
					}
				}
			}, "Pipe Monitor Thread").start();
		}
	}

	/**
	 * For early version of Firefox (<1.5) and Opera (<9.6), subdomain XSS
	 * query may be unsupported.
	 * 
	 * @return whether subdomain XSS is supported or not.
	 * 
	 * @j2sNative
	var dua = navigator.userAgent;
	var dav = navigator.appVersion;
	if (dua.indexOf("Opera") != -1 && parseFloat (dav) < 9.6) {
		return false;
	}
	if (dua.indexOf("Firefox") != -1 && parseFloat (dav) < 1.5) {
		return false;
	}
	if (dua.indexOf("MSIE") != -1 && parseFloat (dav) < 6.0) {
		return false;
	}
	return true;
	 */
	static boolean isSubdomainXSSSupported() {
		return true;
	}
	
	/**
	 * 
	 * @param p
	 * @j2sNative
p.initParameters = function () {
	this.parentDomain = document.domain;
	this.pipeQueryInterval = 1000;
	this.lastQueryReceived = true;
	this.runnable = null;
	var oThis = this;
	with (window.parent) {
		var sph = net.sf.j2s.ajax.SimplePipeHelper;
		var spr = net.sf.j2s.ajax.SimplePipeRequest;
		this.runnable = sph.getPipe(this.key);
		this.pipeQueryInterval = spr.getQueryInterval ();
	}
};
p.initHttpRequest = function () {
	this.xhrHandle = null;
	if (window.XMLHttpRequest) {
		this.xhrHandle = new XMLHttpRequest();
	} else {
		try {
			this.xhrHandle = new ActiveXObject("Msxml2.XMLHTTP");
		} catch (e) {
			this.xhrHandle = new ActiveXObject("Microsoft.XMLHTTP");
		}
	}
	var oThis = this;
	this.xhrHandle.onreadystatechange = function () {
		var state = oThis.xhrHandle.readyState;
		if (state == 4) {
			var pipeData = oThis.xhrHandle.responseText;
			oThis.xhrHandle.onreadystatechange = function () {};
			oThis.xhrHandle = null;
			document.domain = oThis.parentDomain;
			oThis.lastQueryReceived = true;
			with (window.parent) {
				var sph = net.sf.j2s.ajax.SimplePipeHelper;
				var spr = net.sf.j2s.ajax.SimplePipeRequest;
				spr.onPipeDataReceived (pipeData);
				oThis.runnable = sph.getPipe (oThis.key);
			}
		}
	};
};
p.pipeXHRQuery = function (request, method, url, data) {
	if ("GET" == method.toUpperCase ()) {
		request.open (method, url + (url.indexOf ('?') != -1 ? "&" : "?") + data, true, null, null);
		data = null;
	} else {
		request.open (method, url, true, null, null);
	}
	request.setRequestHeader ("User-Agent",
			"Java2Script-Pacemaker/2.0.0 (+http://j2s.sourceforge.net)");
	if (method != null && method.toLowerCase () == "post") {
		request.setRequestHeader ("Content-type", 
				"application/x-www-form-urlencoded");
		if (request.overrideMimeType) {
			request.setRequestHeader ("Connection", "close");
		}
	}
	request.send(data);
};
p.initParameters ();
	 */
	static void subdomainInit(Object p) {
		// for JavaScript only
	}
	
	/**
	 * 
	 * @param p
	 * @j2sNative
return function () {
	if (p.runnable != null) {
		if (p.lastQueryReceived) {
			p.lastQueryReceived = false;
			var method = null;
			var url = null;
			var data = null;
			with (window.parent) {
				method = p.runnable.getPipeMethod ();
				url = p.runnable.getPipeURL ();
				var spr = net.sf.j2s.ajax.SimplePipeRequest;
				data = spr.constructRequest(p.key, spr.PIPE_TYPE_QUERY, true);
			}
			try {
				document.domain = p.originalDomain;
			} catch (e) {};
			p.initHttpRequest ();
			try {
				p.pipeXHRQuery (p.xhrHandle, method, url, data);
			} catch (e) {
				p.xhrHandle.onreadystatechange = function () {};
				p.xhrHandle = null;
				document.domain = p.parentDomain;
				p.lastQueryReceived = true;
			}
		}
		window.setTimeout (arguments.callee, p.pipeQueryInterval);
	}
};
	 */
	static void subdomainLoopQuery(Object p) {
		// for JavaScript only
	}
}
