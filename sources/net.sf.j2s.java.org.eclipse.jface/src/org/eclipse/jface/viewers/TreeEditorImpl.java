/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jface.viewers;

import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;

/**
 * Internal tree viewer implementation.
 * 
 * @since 3.1
 */
/* package */abstract class TreeEditorImpl {

    private CellEditor cellEditor;

    private CellEditor[] cellEditors;

    private ICellModifier cellModifier;

    private String[] columnProperties;

    private Item treeItem;

    private int columnNumber;

    private ICellEditorListener cellEditorListener;

    private FocusListener focusListener;

    private MouseListener mouseListener;

    private int doubleClickExpirationTime;

    private StructuredViewer viewer;

    TreeEditorImpl(StructuredViewer viewer) {
        this.viewer = viewer;
        initCellEditorListener();
    }

    /**
     * Returns this <code>TreeViewerImpl</code> viewer
     * 
     * @return the viewer
     */
    public StructuredViewer getViewer() {
        return viewer;
    }

    private void activateCellEditor() {
        if (cellEditors != null) {
            if (cellEditors[columnNumber] != null && cellModifier != null) {
                Object element = treeItem.getData();
                String property = columnProperties[columnNumber];
                if (cellModifier.canModify(element, property)) {
                    cellEditor = cellEditors[columnNumber];
                    //tree.showSelection();
                    cellEditor.addListener(cellEditorListener);
                    Object value = cellModifier.getValue(element, property);
                    cellEditor.setValue(value);
                    // Tricky flow of control here:
                    // activate() can trigger callback to cellEditorListener which will clear cellEditor
                    // so must get control first, but must still call activate() even if there is no control.
                    final Control control = cellEditor.getControl();
                    cellEditor.activate();
                    if (control == null)
                        return;
                    setLayoutData(cellEditor.getLayoutData());
                    setEditor(control, treeItem, columnNumber);
                    cellEditor.setFocus();
                    if (focusListener == null) {
                        focusListener = new FocusAdapter() {
                            public void focusLost(FocusEvent e) {
                                applyEditorValue();
                            }
                        };
                    }
                    control.addFocusListener(focusListener);
                    mouseListener = new MouseAdapter() {
                        public void mouseDown(MouseEvent e) {
                            // time wrap?	
                            // check for expiration of doubleClickTime
                            if (e.time <= doubleClickExpirationTime) {
                                control.removeMouseListener(mouseListener);
                                cancelEditing();
                                handleDoubleClickEvent();
                            } else if (mouseListener != null) {
                                control.removeMouseListener(mouseListener);
                            }
                        }
                    };
                    control.addMouseListener(mouseListener);
                }
            }
        }
    }

    /**
     * Activate a cell editor for the given mouse position.
     */
    private void activateCellEditor(MouseEvent event) {
        if (treeItem == null || treeItem.isDisposed()) {
            //item no longer exists
            return;
        }
        int columnToEdit;
        int columns = getColumnCount();
        if (columns == 0) {
            // If no TreeColumn, Tree acts as if it has a single column
            // which takes the whole width.
            columnToEdit = 0;
        } else {
            columnToEdit = -1;
            for (int i = 0; i < columns; i++) {
                Rectangle bounds = getBounds(treeItem, i);
                if (bounds.contains(event.x, event.y)) {
                    columnToEdit = i;
                    break;
                }
            }
            if (columnToEdit == -1) {
                return;
            }
        }

        columnNumber = columnToEdit;
        activateCellEditor();
    }

    /**
     * Deactivates the currently active cell editor.
     */
    public void applyEditorValue() {
        CellEditor c = this.cellEditor;
        if (c != null) {
            // null out cell editor before calling save
            // in case save results in applyEditorValue being re-entered
            // see 1GAHI8Z: ITPUI:ALL - How to code event notification when using cell editor ?
            this.cellEditor = null;
            Item t = this.treeItem;
            // don't null out tree item -- same item is still selected
            if (t != null && !t.isDisposed()) {
                saveEditorValue(c, t);
            }
            setEditor(null, null, 0);
            c.removeListener(cellEditorListener);
            Control control = c.getControl();
            if (control != null) {
                if (mouseListener != null) {
                    control.removeMouseListener(mouseListener);
                }
                if (focusListener != null) {
                    control.removeFocusListener(focusListener);
                }
            }
            c.deactivate();
        }
    }

    /**
     * Cancels the active cell editor, without saving the value 
     * back to the domain model.
     */
    public void cancelEditing() {
        if (cellEditor != null) {
            setEditor(null, null, 0);
            cellEditor.removeListener(cellEditorListener);
            CellEditor oldEditor = cellEditor;
            cellEditor = null;
            oldEditor.deactivate();
        }
    }

    /**
     * Start editing the given element. 
     * @param element
     * @param column
     */
    public void editElement(Object element, int column) {
        if (cellEditor != null)
            applyEditorValue();

        setSelection(new StructuredSelection(element), true);
        Item[] selection = getSelection();
        if (selection.length != 1)
            return;

        treeItem = selection[0];

        // Make sure selection is visible
        showSelection();
        columnNumber = column;
        activateCellEditor();

    }

    abstract Rectangle getBounds(Item item, int columnNumber);

    /**
     * Get the Cell Editors for the receiver.
     * @return CellEditor[]
     */
    public CellEditor[] getCellEditors() {
        return cellEditors;
    }

    /**
     * Get the cell modifier for the receiver.
     * @return ICellModifier
     */
    public ICellModifier getCellModifier() {
        return cellModifier;
    }

    abstract int getColumnCount();

    /**
     * Get the column properties for the receiver.
     * @return Object[]
     */
    public Object[] getColumnProperties() {
        return columnProperties;
    }

    abstract Item[] getSelection();

    /**
     * Handles the mouse down event; activates the cell editor.
     * @param event
     */
    public void handleMouseDown(MouseEvent event) {
        if (event.button != 1)
            return;

        if (cellEditor != null)
            applyEditorValue();

        // activate the cell editor immediately.  If a second mouseDown
        // is received prior to the expiration of the doubleClick time then
        // the cell editor will be deactivated and a doubleClick event will
        // be processed.
        //
        doubleClickExpirationTime = event.time
                + Display.getCurrent().getDoubleClickTime();

        Item[] items = getSelection();
        // Do not edit if more than one row is selected.
        if (items.length != 1) {
            treeItem = null;
            return;
        }
        treeItem = items[0];
        activateCellEditor(event);
    }

    private void initCellEditorListener() {
        cellEditorListener = new ICellEditorListener() {
            public void editorValueChanged(boolean oldValidState,
                    boolean newValidState) {
                // Ignore.
            }

            public void cancelEditor() {
                TreeEditorImpl.this.cancelEditing();
            }

            public void applyEditorValue() {
                TreeEditorImpl.this.applyEditorValue();
            }
        };
    }

    /**
     * Return whether or not there is an active cell editor.
     * @return boolean <code>true</code> if there is an active cell editor; otherwise
     * <code>false</code> is returned.
     */
    public boolean isCellEditorActive() {
        return cellEditor != null;
    }

    /**
     * Saves the value of the currently active cell editor,
     * by delegating to the cell modifier.
     */
    private void saveEditorValue(CellEditor cellEditor, Item treeItem) {
        if (cellModifier != null) {
            String property = null;
            if (columnProperties != null
                    && columnNumber < columnProperties.length)
                property = columnProperties[columnNumber];
            cellModifier.modify(treeItem, property, cellEditor.getValue());
        }
    }

    /**
     * Set the cell editors for the receiver.
     * @param editors
     */
    public void setCellEditors(CellEditor[] editors) {
        this.cellEditors = editors;
    }

    /**
     * Set the cell modifier for the receiver.
     * @param modifier
     */
    public void setCellModifier(ICellModifier modifier) {
        this.cellModifier = modifier;
    }

    /**
     * Set the column properties for the receiver.
     * @param columnProperties
     */
    public void setColumnProperties(String[] columnProperties) {
        this.columnProperties = columnProperties;
    }

    abstract void setEditor(Control w, Item item, int fColumnNumber);

    abstract void setLayoutData(CellEditor.LayoutData layoutData);

    abstract void setSelection(StructuredSelection selection, boolean b);

    abstract void showSelection();

    abstract void handleDoubleClickEvent();
}
