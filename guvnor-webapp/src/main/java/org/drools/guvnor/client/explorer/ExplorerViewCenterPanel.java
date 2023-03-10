/*
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.guvnor.client.explorer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import org.drools.guvnor.client.common.LoadingPopup;
import org.drools.guvnor.client.messages.Constants;
import org.drools.guvnor.client.packages.PackageEditorWrapper;
import org.drools.guvnor.client.ruleeditor.GuvnorEditor;
import org.drools.guvnor.client.util.ScrollTabLayoutPanel;
import org.drools.guvnor.client.util.TabOpener;

/**
 * This is the tab panel manager.
 */
public class ExplorerViewCenterPanel extends Composite {

    private final ScrollTabLayoutPanel tabLayoutPanel;

    private MultiKeyMap<Panel> openedTabs = new MultiKeyMap<Panel>();
    private static int id = 0;

    /**
     * to keep track of what is dirty, filthy
     */
    private Map<String, GuvnorEditor> openedAssetEditors = new HashMap<String, GuvnorEditor>();
    private Map<String, PackageEditorWrapper> openedPackageEditors = new HashMap<String, PackageEditorWrapper>();

    private Map<Panel, String[]> itemWidgets = new HashMap<Panel, String[]>();

    public ExplorerViewCenterPanel() {
        tabLayoutPanel = new ScrollTabLayoutPanel(2,
                Unit.EM);

        Constants constants = GWT.create(Constants.class);

        DockLayoutPanel layoutPanel = new DockLayoutPanel(Unit.EM);
        Button button = new Button(constants.CloseAllItems());
        button.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                closeAll();
            }
        });

        HorizontalPanel bottomPanel = new HorizontalPanel();
        bottomPanel.setWidth("100%");
        bottomPanel.setStyleName("bottom-panel");
        bottomPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
        bottomPanel.add(button);

        layoutPanel.addSouth(bottomPanel, 2);
        layoutPanel.add(tabLayoutPanel);

        initWidget(layoutPanel);

        TabOpener.initIstance(this);
        TabOpener.getInstance().openFind();
    }

    /**
     * Add a new tab. Should only do this if have checked showIfOpen to avoid dupes being opened.
     * @param tabname The displayed tab name.
     * @param widget The contents.
     * @param key A key which is unique.
     */
    public void addTab(final String tabname,
            IsWidget widget,
            final String key) {
        addTab(tabname,
                widget,
                new String[]{key});
    }

    /**
     * Add a new tab. Should only do this if have checked showIfOpen to avoid dupes being opened.
     * @param tabname The displayed tab name.
     * @param widget The contents.
     * @param keys An array of keys which are unique.
     */
    public void addTab(final String tabname,
            IsWidget widget,
            final String[] keys) {
        final String panelId = (keys.length == 1 ? keys[0] + id++ : Arrays.toString(keys) + id++);

        ScrollPanel localTP = new ScrollPanel();
        localTP.add(widget);
        tabLayoutPanel.add(localTP,
                newClosableLabel(localTP,
                        tabname));
        tabLayoutPanel.selectTab(localTP);

        //TODO: Dirtyable
        /*        localTP.ad( new PanelListenerAdapter() {
                    public void onDestroy(Component component) {
                        Panel p = openedTabs.remove( keys );
                        if ( p != null ) {
                            p.destroy();
                        }
                        openedAssetEditors.remove( panelId );
                        openedPackageEditors.remove( tabname );
                    }
                } );
        */
        if (widget instanceof GuvnorEditor) {
            this.openedAssetEditors.put(panelId,
                    (GuvnorEditor) widget);
        } else if (widget instanceof PackageEditorWrapper) {
            this.getOpenedPackageEditors().put(tabname,
                    (PackageEditorWrapper) widget);
        }

        openedTabs.put(keys,
                localTP);
        itemWidgets.put(localTP,
                keys);
    }

    private Widget newClosableLabel(final Panel panel,
            final String title) {
        ClosableLabel closableLabel = new ClosableLabel(title);

        closableLabel.addCloseHandler(new CloseHandler<ClosableLabel>() {
            public void onClose(CloseEvent<ClosableLabel> event) {
                int widgetIndex = tabLayoutPanel.getWidgetIndex(panel);
                if (widgetIndex == tabLayoutPanel.getSelectedIndex()) {
                    if (isOnlyOneTabLeft()) {
                        tabLayoutPanel.clear();
                    } else {
                        tabLayoutPanel.selectTab(widgetIndex - 1);
                    }
                }
                tabLayoutPanel.remove(widgetIndex);
                String[] keys = itemWidgets.remove(panel);
                openedTabs.remove(keys);
            }

            private boolean isOnlyOneTabLeft() {
                return tabLayoutPanel.getWidgetCount() == 1;
            }
        });

        return closableLabel;
    }

    /**
     * Will open if existing. If not it will return false;
     */
    public boolean showIfOpen(String key) {
        if (openedTabs.containsKey(key)) {
            LoadingPopup.close();
            Panel tpi = (Panel) openedTabs.get(key);
            tabLayoutPanel.selectTab(tpi);
            return true;
        }
        return false;
    }

    public void close(String key) {
        Panel tpi = openedTabs.remove(key);

        int widgetIndex = tabLayoutPanel.getWidgetIndex(tpi);
        if (widgetIndex == tabLayoutPanel.getSelectedIndex()) {
            if (widgetIndex > 0) {
                tabLayoutPanel.selectTab(widgetIndex - 1);
            }
        }

        tabLayoutPanel.remove(widgetIndex);
        itemWidgets.remove(tpi);
    }

    public Map<String, PackageEditorWrapper> getOpenedPackageEditors() {
        return openedPackageEditors;
    }

    public void closeAll() {
        tabLayoutPanel.clear();
        openedTabs.clear();
        openedAssetEditors.clear();
        openedPackageEditors.clear();
        itemWidgets.clear();
    }
}
