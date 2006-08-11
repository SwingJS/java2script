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
package net.sf.j2s.ui.property;

import net.sf.j2s.ui.classpath.UnitClass;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.internal.debug.core.JDIDebugPlugin;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.views.navigator.ResourceSorter;

/**
 * @author josson smith
 *
 * 2006-2-1
 */
public class J2SAbandonClassesAction implements SelectionListener {
	J2SConfigPage page;
	
	private ISelectionStatusValidator validator= new ISelectionStatusValidator() {
		public IStatus validate(Object[] selection) {
			if (selection.length == 0) {
				return new Status(IStatus.ERROR, JDIDebugPlugin.getUniqueIdentifier(), 0, "", null); //$NON-NLS-1$
			}
//			for (int i= 0; i < selection.length; i++) {
//				if (!(selection[i] instanceof IFile)) {
//					return new Status(IStatus.ERROR, JDIDebugPlugin.getUniqueIdentifier(), 0, "", null); //$NON-NLS-1$
//				}					
//			}
			return new Status(IStatus.OK, JDIDebugPlugin.getUniqueIdentifier(), 0, "", null); //$NON-NLS-1$
		}			
	};

	
	public J2SAbandonClassesAction(J2SConfigPage page) {
		super();
		this.page = page;
	}

	public void widgetSelected(SelectionEvent e) {
//		ViewerFilter filter= new ArchiveFilter(new ArrayList());
//		String j2sPath = page.j2sFile.getAbsolutePath();
		ILabelProvider lp= new J2SClasspathLabelProvider();
		ITreeContentProvider cp= new J2SClasspathContentProvider();

		ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(e.display.getActiveShell(), lp, cp) {
			protected TreeViewer createTreeViewer(Composite parent) {
				TreeViewer treeViewer = super.createTreeViewer(parent);
				
//				if (project != null) {
//					treeViewer.setSelection(new StructuredSelection(project));
//					treeViewer.expandToLevel(project, 2);
//				}
				return treeViewer;
			}
		};
		dialog.setValidator(validator);
		dialog.setTitle("Classes Selection"); //$NON-NLS-1$
		dialog.setMessage("Choose classes to be abandoned"); //$NON-NLS-1$
//		dialog.addFilter(filter);
		//dialog.setInitialSelection(page.j2s);
		//if (configuration == null) {
		boolean alreadyUpdated = false;
		UnitClass[] unitClasses = page.classpathModel.getUnitClasses();
		for (int i = 0; i < unitClasses.length; i++) {
			if (!unitClasses[i].getAbsoluteFile().exists()) {
				page.classpathModel.removeUnitClass(unitClasses[i]);
				alreadyUpdated = true;
			}
		}
			dialog.setInput(new J2SCategory(page.classpathModel, "Classes"));
//		} else {
//			try {
//				String projectName = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, (String)null);
//				dialog.setInput(ResourcesPlugin.getWorkspace().getRoot().getProject(projectName));
//			} catch (CoreException e) {
//				e.printStackTrace();
//				dialog.setInput(ResourcesPlugin.getWorkspace().getRoot());
//			}
//		}
		dialog.setSorter(new ResourceSorter(ResourceSorter.NAME));

		if (dialog.open() == Window.OK) {
			Object[] expandedElements = page.viewer.getExpandedElements();
			Object[] elements= dialog.getResult();
			for (int i= 0; i < elements.length; i++) {
				page.classpathModel.abandonUnitClass((UnitClass) elements[i]);
			}
			ScrollBar bar =  page.viewer.getTree().getVerticalBar();
			double selection = 0;
			if (bar != null) {
				selection = (0.0 + bar.getSelection()) / bar.getMaximum();
			}
			 page.viewer.refresh();
			//viewer.expandToLevel(2);
			 page.viewer.setExpandedElements(expandedElements);
			if (bar != null) {
				bar.setSelection((int) Math.round(selection * bar.getMaximum()));
			}
			 page.updateButtonGroup();
			 page.fireConfigModified();
		} else if (alreadyUpdated) {
			//MessageDialog.openInformation(page.getShell(), "Detected Changes", "Java2Script detects you rename classes or remove unused classes.\r\nThe Java2Script properties is marked as modified.");
			page.fireConfigModified();
		}
	}

	public void widgetDefaultSelected(SelectionEvent e) {
		// TODO Auto-generated method stub

	}

}
