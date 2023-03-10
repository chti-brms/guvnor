/*
 * Copyright 2011 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.guvnor.client.explorer;

import com.google.gwt.place.shared.Place;
import com.google.gwt.place.shared.PlaceController;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.gwt.user.client.ui.IsWidget;
import org.drools.guvnor.client.explorer.PerspectivesPanelView.Presenter;

import java.util.HashMap;
import java.util.Map;


public class PerspectivesPanel implements Presenter, AcceptsOneWidget {

    private final PerspectivesPanelView view;
    private final PlaceController placeController;

    private Map<String, Place> perspectives = new HashMap<String, Place>();

    public PerspectivesPanel(PerspectivesPanelView view, PlaceController placeController) {
        this.view = view;
        this.view.setPresenter(this);
        this.placeController = placeController;

    }

    public void setWidget(IsWidget widget) {
        if (widget != null) {
            view.setWidget(widget);
        }
    }

    public PerspectivesPanelView getView() {
        return view;
    }

    public void setUserName(String userName) {
        view.setUserName(userName);
    }

    public void addPerspective(Perspective perspective) {
        String name = perspective.getName();
        perspectives.put(name, perspective);
        view.addPerspectiveToList(name, name);
    }

    public void onPerspectiveChange(String perspectiveId) throws UnknownPerspective {
        placeController.goTo(perspectives.get(perspectiveId));
    }
    }
