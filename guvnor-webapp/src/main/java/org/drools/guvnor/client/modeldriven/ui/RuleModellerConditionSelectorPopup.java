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
package org.drools.guvnor.client.modeldriven.ui;

import org.drools.guvnor.client.common.InfoPopup;
import org.drools.guvnor.client.modeldriven.HumanReadable;
import org.drools.guvnor.client.security.Capabilities;
import org.drools.guvnor.client.security.CapabilitiesManager;
import org.drools.ide.common.client.modeldriven.brl.CompositeFactPattern;
import org.drools.ide.common.client.modeldriven.brl.DSLSentence;
import org.drools.ide.common.client.modeldriven.brl.FactPattern;
import org.drools.ide.common.client.modeldriven.brl.FreeFormLine;
import org.drools.ide.common.client.modeldriven.brl.FromAccumulateCompositeFactPattern;
import org.drools.ide.common.client.modeldriven.brl.FromCollectCompositeFactPattern;
import org.drools.ide.common.client.modeldriven.brl.FromCompositeFactPattern;
import org.drools.ide.common.client.modeldriven.brl.FromEntryPointFactPattern;
import org.drools.ide.common.client.modeldriven.brl.RuleModel;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;

/**
 * Pop-up for adding Conditions to the (RuleModeller) guided editor
 */
public class RuleModellerConditionSelectorPopup extends AbstractRuleModellerSelectorPopup {

    public RuleModellerConditionSelectorPopup(RuleModel model,
                                              RuleModeller ruleModeller,
                                              String packageName,
                                              Integer position) {
        super( model,
               ruleModeller,
               packageName,
               position );
    }

    @Override
    protected String getPopupTitle() {
        return constants.AddAConditionToTheRule();
    }

    @Override
    public Widget getContent() {
        if ( position == null ) {
            positionCbo.addItem( constants.Bottom(),
                                 String.valueOf( this.model.lhs.length ) );
            positionCbo.addItem( constants.Top(),
                                 "0" );
            for ( int i = 1; i < model.lhs.length; i++ ) {
                positionCbo.addItem( constants.Line0( i ),
                                     String.valueOf( i ) );
            }
        } else {
            //if position is fixed, we just add one element to the drop down.
            positionCbo.addItem( String.valueOf( position ) );
            positionCbo.setSelectedIndex( 0 );
        }

        if ( completions.getDSLConditions().length == 0 && completions.getFactTypes().length == 0 ) {
            layoutPanel.addRow( new HTML( "<div class='highlight'>" + constants.NoModelTip() + "</div>" ) ); 
        }

        //only show the drop down if we are not using fixed position.
        if ( position == null ) {
            HorizontalPanel hp0 = new HorizontalPanel();
            hp0.add( new HTML( constants.PositionColon() ) );
            hp0.add( positionCbo );
            hp0.add( new InfoPopup( constants.PositionColon(),
                                    constants.ConditionPositionExplanation() ) );
            layoutPanel.addRow( hp0 );
        }

        choices = makeChoicesListBox();
        choicesPanel.add( choices );
        layoutPanel.addRow( choicesPanel );

        HorizontalPanel hp = new HorizontalPanel();
        Button ok = new Button( constants.OK() );
        hp.add( ok );
        ok.addClickHandler( new ClickHandler() {

            public void onClick(ClickEvent event) {
                selectSomething();
            }
        } );

        Button cancel = new Button( constants.Cancel() );
        hp.add( cancel );
        cancel.addClickHandler( new ClickHandler() {

            public void onClick(ClickEvent event) {
                hide();
            }
        } );

        layoutPanel.addRow( hp );

        this.setAfterShow( new Command() {

            public void execute() {
                choices.setFocus( true );
            }
        } );

        return layoutPanel;
    }

    private ListBox makeChoicesListBox() {
        choices = new ListBox( true );
        choices.setPixelSize( getChoicesWidth(),
                              getChoicesHeight() );

        choices.addKeyUpHandler( new KeyUpHandler() {
            public void onKeyUp(com.google.gwt.event.dom.client.KeyUpEvent event) {
                if ( event.getNativeKeyCode() == KeyCodes.KEY_ENTER ) {
                    selectSomething();
                }
            }
        } );

        addDSLSentences();
        addFacts();
        addExistentialConditionalElements();
        addFromConditionalElements();
        addFreeFormDrl();

        return choices;
    }

    // The list of DSL sentences
    private void addDSLSentences() {
        if ( completions.getDSLConditions().length > 0 ) {
            for ( int i = 0; i < completions.getDSLConditions().length; i++ ) {
                final DSLSentence sen = completions.getDSLConditions()[i];
                String key = "DSL" + i;
                choices.addItem( sen.toString(),
                                 key );
                cmds.put( key,
                          new Command() {

                              public void execute() {
                                  addNewDSLLhs( sen,
                                                Integer.parseInt( positionCbo.getValue( positionCbo.getSelectedIndex() ) ) );
                                  hide();
                              }
                          } );
            }
        }
    }

    // The list of facts
    private void addFacts() {
        if ( completions.getFactTypes().length > 0 ) {
            choices.addItem( SECTION_SEPARATOR );

            for ( int i = 0; i < completions.getFactTypes().length; i++ ) {
                final String f = completions.getFactTypes()[i];
                String key = "NF" + f;

                choices.addItem( f + " ...",
                                 key );
                cmds.put( key,
                          new Command() {

                              public void execute() {
                                  addNewFact( f,
                                              Integer.parseInt( positionCbo.getValue( positionCbo.getSelectedIndex() ) ) );
                                  hide();
                              }
                          } );
            }
        }
    }

    // The list of existential CEs
    private void addExistentialConditionalElements() {
        String ces[] = HumanReadable.CONDITIONAL_ELEMENTS;

        choices.addItem( SECTION_SEPARATOR );
        for ( int i = 0; i < ces.length; i++ ) {
            final String ce = ces[i];
            String key = "CE" + ce;
            choices.addItem( HumanReadable.getCEDisplayName( ce ) + " ...",
                             key );
            cmds.put( key,
                      new Command() {

                          public void execute() {
                              addNewCE( ce,
                                        Integer.parseInt( positionCbo.getValue( positionCbo.getSelectedIndex() ) ) );
                              hide();
                          }
                      } );
        }
    }

    // The list of from CEs
    private void addFromConditionalElements() {
        String fces[] = HumanReadable.FROM_CONDITIONAL_ELEMENTS;

        choices.addItem( SECTION_SEPARATOR );
        for ( int i = 0; i < fces.length; i++ ) {
            final String ce = fces[i];
            String key = "FCE" + ce;
            choices.addItem( HumanReadable.getCEDisplayName( ce ) + " ...",
                             key );
            cmds.put( key,
                      new Command() {

                          public void execute() {
                              addNewFCE( ce,
                                         Integer.parseInt( positionCbo.getValue( positionCbo.getSelectedIndex() ) ) );
                              hide();
                          }
                      } );
        }
    }

    // Free form DRL
    private void addFreeFormDrl() {
        if ( CapabilitiesManager.getInstance().shouldShow( Capabilities.SHOW_PACKAGE_VIEW ) ) {
            choices.addItem( SECTION_SEPARATOR );
            choices.addItem( constants.FreeFormDrl(),
                             "FF" );
            cmds.put( "FF",
                      new Command() {

                          public void execute() {
                              model.addLhsItem( new FreeFormLine(),
                                                Integer.parseInt( positionCbo.getValue( positionCbo.getSelectedIndex() ) ) );
                              hide();
                          }
                      } );

        }
    }

    private void addNewDSLLhs(DSLSentence sentence,
                                int position) {
        model.addLhsItem( sentence.copy(),
                          position );
    }

    private void addNewFact(String itemText,
                              int position) {
        this.model.addLhsItem( new FactPattern( itemText ),
                               position );
    }

    private void addNewCE(String s,
                            int position) {
        this.model.addLhsItem( new CompositeFactPattern( s ),
                               position );
    }

    private void addNewFCE(String type,
                             int position) {
        FromCompositeFactPattern p = null;
        if ( type.equals( "from" ) ) {
            p = new FromCompositeFactPattern();
        } else if ( type.equals( "from accumulate" ) ) {
            p = new FromAccumulateCompositeFactPattern();
        } else if ( type.equals( "from collect" ) ) {
            p = new FromCollectCompositeFactPattern();
        } else if ( type.equals( "from entry-point" ) ) {
            p = new FromEntryPointFactPattern();
        }

        this.model.addLhsItem( p,
                               position );
    }

}
