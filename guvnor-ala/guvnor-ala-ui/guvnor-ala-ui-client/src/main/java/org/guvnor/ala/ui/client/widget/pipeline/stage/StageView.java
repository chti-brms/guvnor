/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
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

package org.guvnor.ala.ui.client.widget.pipeline.stage;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.jboss.errai.common.client.dom.Div;
import org.jboss.errai.common.client.dom.Span;
import org.jboss.errai.ui.shared.api.annotations.DataField;
import org.jboss.errai.ui.shared.api.annotations.Templated;

@Dependent
@Templated
public class StageView
        implements org.jboss.errai.ui.client.local.api.IsElement,
                   StagePresenter.View {

    private static final String DONE_COLOR = "#3f9c35";

    private static final String ERROR_COLOR = "#ffff00";

    @Inject
    @DataField("stage-container")
    private Div container;

    @Inject
    @DataField("name")
    private Span name;

    private StagePresenter presenter;

    @Override
    public void init(final StagePresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void setExecutingState() {
        container.getStyle().removeProperty("border-color");
        container.getStyle().setProperty("animation",
                                         "border-pulsate 2s infinite");
    }

    @Override
    public void setDoneState() {
        container.getStyle().setProperty("border-color",
                                         DONE_COLOR);
        container.getStyle().removeProperty("animation");
    }

    @Override
    public void setErrorState() {
        container.getStyle().setProperty("border-color",
                                         ERROR_COLOR);
        container.getStyle().removeProperty("animation");
    }

    @Override
    public void setName(final String name) {
        this.name.setTextContent(name);
    }
}