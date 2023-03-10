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

package org.drools.ide.common.server.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.core.util.ReflectiveVisitor;
import org.drools.ide.common.client.modeldriven.FieldNature;
import org.drools.ide.common.client.modeldriven.SuggestionCompletionEngine;
import org.drools.ide.common.client.modeldriven.brl.ActionCallMethod;
import org.drools.ide.common.client.modeldriven.brl.ActionFieldFunction;
import org.drools.ide.common.client.modeldriven.brl.ActionFieldValue;
import org.drools.ide.common.client.modeldriven.brl.ActionGlobalCollectionAdd;
import org.drools.ide.common.client.modeldriven.brl.ActionInsertFact;
import org.drools.ide.common.client.modeldriven.brl.ActionInsertLogicalFact;
import org.drools.ide.common.client.modeldriven.brl.ActionRetractFact;
import org.drools.ide.common.client.modeldriven.brl.ActionSetField;
import org.drools.ide.common.client.modeldriven.brl.ActionUpdateField;
import org.drools.ide.common.client.modeldriven.brl.BaseSingleFieldConstraint;
import org.drools.ide.common.client.modeldriven.brl.CEPWindow;
import org.drools.ide.common.client.modeldriven.brl.CompositeFactPattern;
import org.drools.ide.common.client.modeldriven.brl.CompositeFieldConstraint;
import org.drools.ide.common.client.modeldriven.brl.ConnectiveConstraint;
import org.drools.ide.common.client.modeldriven.brl.DSLSentence;
import org.drools.ide.common.client.modeldriven.brl.ExpressionFormLine;
import org.drools.ide.common.client.modeldriven.brl.FactPattern;
import org.drools.ide.common.client.modeldriven.brl.FieldConstraint;
import org.drools.ide.common.client.modeldriven.brl.FreeFormLine;
import org.drools.ide.common.client.modeldriven.brl.FromAccumulateCompositeFactPattern;
import org.drools.ide.common.client.modeldriven.brl.FromCollectCompositeFactPattern;
import org.drools.ide.common.client.modeldriven.brl.FromCompositeFactPattern;
import org.drools.ide.common.client.modeldriven.brl.FromEntryPointFactPattern;
import org.drools.ide.common.client.modeldriven.brl.HasParameterizedOperator;
import org.drools.ide.common.client.modeldriven.brl.IAction;
import org.drools.ide.common.client.modeldriven.brl.IFactPattern;
import org.drools.ide.common.client.modeldriven.brl.IPattern;
import org.drools.ide.common.client.modeldriven.brl.RuleAttribute;
import org.drools.ide.common.client.modeldriven.brl.RuleModel;
import org.drools.ide.common.client.modeldriven.brl.SingleFieldConstraint;
import org.drools.ide.common.client.modeldriven.brl.SingleFieldConstraintEBLeftSide;
import org.drools.ide.common.shared.SharedConstants;

/**
 * This class persists the rule model to DRL and back
 */
public class BRDRLPersistence
    implements
    BRLPersistence {

    private static final BRLPersistence INSTANCE = new BRDRLPersistence();

    protected BRDRLPersistence() {
    }

    public static BRLPersistence getInstance() {
        return INSTANCE;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.drools.ide.common.server.util.BRLPersistence#marshal(org.drools.guvnor
     * .client.modeldriven.brl.RuleModel)
     */
    public String marshal(RuleModel model) {
        return marshalRule( model );
    }

    protected String marshalRule(RuleModel model) {
        boolean isDSLEnhanced = model.hasDSLSentences();

        StringBuilder buf = new StringBuilder();
        this.marshalHeader( model,
                            buf );
        this.marshalMetadata( buf,
                              model );
        this.marshalAttributes( buf,
                                model );

        buf.append( "\twhen\n" );
        this.marshalLHS( buf,
                         model,
                         isDSLEnhanced );
        buf.append( "\tthen\n" );
        this.marshalRHS( buf,
                         model,
                         isDSLEnhanced );
        this.marshalFooter( buf );
        return buf.toString();
    }

    protected void marshalFooter(StringBuilder buf) {
        buf.append( "end\n" );
    }

    protected void marshalHeader(RuleModel model,
                                 StringBuilder buf) {
        buf.append( "rule \"" + marshalRuleName( model ) + "\"" );
        if ( null != model.parentName && model.parentName.length() > 0 ) {
            buf.append( " extends \"" + model.parentName + "\"\n" );
        } else {
            buf.append( '\n' );
        }
    }

    protected String marshalRuleName(RuleModel model) {
        return model.name;
    }

    /**
     * @see org.drools.ide.common.server.util.BRLPersistence#unmarshal(java.lang.String)
     */
    public RuleModel unmarshal(String str) {
        throw new UnsupportedOperationException( "Still not possible to convert pure DRL to RuleModel" );
    }

    /**
     * Marshal model attributes
     * 
     * @param buf
     * @param model
     */
    private void marshalAttributes(StringBuilder buf,
                                   RuleModel model) {
        boolean hasDialect = false;
        for ( int i = 0; i < model.attributes.length; i++ ) {
            RuleAttribute attr = model.attributes[i];

            buf.append( "\t" );
            buf.append( attr );

            buf.append( "\n" );
            if ( attr.attributeName.equals( "dialect" ) ) {
                hasDialect = true;
            }
        }
        // Un comment below for mvel
        if ( !hasDialect ) {
            RuleAttribute attr = new RuleAttribute( "dialect",
                                                    "mvel" );
            buf.append( "\t" );
            buf.append( attr );
            buf.append( "\n" );
        }
    }

    /**
     * Marshal model metadata
     * 
     * @param buf
     * @param model
     */
    private void marshalMetadata(StringBuilder buf,
                                 RuleModel model) {
        if ( model.metadataList != null ) {
            for ( int i = 0; i < model.metadataList.length; i++ ) {
                buf.append( "\t" ).append( model.metadataList[i] ).append( "\n" );
            }
        }
    }

    /**
     * Marshal LHS patterns
     * 
     * @param buf
     * @param model
     */
    private void marshalLHS(StringBuilder buf,
                            RuleModel model,
                            boolean isDSLEnhanced) {
        String indentation = "\t\t";
        String nestedIndentation = indentation;
        boolean isNegated = model.isNegated();
        if ( model.lhs != null ) {
            if ( isNegated ) {
                nestedIndentation += "\t";
                buf.append( indentation );
                buf.append( "not (\n" );
            }
            LHSPatternVisitor visitor = new LHSPatternVisitor( isDSLEnhanced,
                                                               buf,
                                                               nestedIndentation,
                                                               isNegated );
            for ( IPattern cond : model.lhs ) {
                visitor.visit( cond );
            }
            if ( model.isNegated() ) {
                //Delete the spurious " and ", added by LHSPatternVisitor.visitFactPattern, when the rule is negated
                buf.delete( buf.length() - 5,
                            buf.length() );
                buf.append( "\n" );
                buf.append( indentation );
                buf.append( ")\n" );
            }
        }
    }

    private void marshalRHS(StringBuilder buf,
                            RuleModel model,
                            boolean isDSLEnhanced) {
        String indentation = "\t\t";
        if ( model.rhs != null ) {
            Map<String, List<ActionFieldValue>> classes = getRHSClassDependencies( model );
            if ( classes.containsKey( SuggestionCompletionEngine.TYPE_DATE ) ) {
                buf.append( indentation );
                buf.append( "java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(\"" + System.getProperty( "drools.dateformat" ) + "\");\n" );
            }

            RHSActionVisitor actionVisitor = new RHSActionVisitor( isDSLEnhanced,
                                                                   buf,
                                                                   indentation );
            for ( IAction action : model.rhs ) {
                actionVisitor.visit( action );
            }
        }
    }

    private Map<String, List<ActionFieldValue>> getRHSClassDependencies(RuleModel model) {
        if ( model != null ) {
            RHSClassDependencyVisitor dependencyVisitor = new RHSClassDependencyVisitor();
            for ( IAction action : model.rhs ) {
                dependencyVisitor.visit( action );
            }
            return dependencyVisitor.getRHSClasses();
        }

        Map<String, List<ActionFieldValue>> empty = Collections.emptyMap();
        return empty;
    }

    public static class LHSPatternVisitor extends ReflectiveVisitor {

        private StringBuilder buf;
        private boolean       isDSLEnhanced;
        private boolean       isPatternNegated;
        private String        indentation;

        public LHSPatternVisitor(boolean isDSLEnhanced,
                                 StringBuilder b,
                                 String indentation,
                                 boolean isPatternNegated) {
            this.isPatternNegated = isPatternNegated;
            this.isDSLEnhanced = isDSLEnhanced;
            this.indentation = indentation;
            buf = b;
        }

        public void visitFactPattern(FactPattern pattern) {
            buf.append( indentation );
            if ( isDSLEnhanced ) {
                // adding passthrough markup
                buf.append( ">" );
            }
            generateFactPattern( pattern );
            if ( isPatternNegated ) {
                buf.append( " and " );
            }
            buf.append( "\n" );
        }

        public void visitFreeFormLine(FreeFormLine ffl) {

            this.buf.append( indentation );
            if ( isDSLEnhanced ) {
                buf.append( ">" );
            }
            this.buf.append( ffl.text );
            this.buf.append( "\n" );
        }

        public void visitCompositeFactPattern(CompositeFactPattern pattern) {
            buf.append( indentation );
            if ( isDSLEnhanced ) {
                // adding passthrough markup
                buf.append( ">" );
            }
            if ( CompositeFactPattern.COMPOSITE_TYPE_EXISTS.equals( pattern.type ) ) {
                renderCompositeFOL( pattern );
            } else if ( CompositeFactPattern.COMPOSITE_TYPE_NOT.equals( pattern.type ) ) {
                renderCompositeFOL( pattern );
            } else if ( CompositeFactPattern.COMPOSITE_TYPE_OR.equals( pattern.type ) ) {
                buf.append( "( " );
                if ( pattern.getPatterns() != null ) {
                    for ( int i = 0; i < pattern.getPatterns().length; i++ ) {
                        if ( i > 0 ) {
                            buf.append( " " );
                            buf.append( pattern.type );
                            buf.append( " " );
                        }
                        renderSubPattern( pattern,
                                          i );
                    }
                }
                buf.append( " )\n" );
            }
        }

        public void visitFromCompositeFactPattern(FromCompositeFactPattern pattern) {
            buf.append( indentation );
            if ( isDSLEnhanced ) {
                // adding passthrough markup
                buf.append( ">" );
            }
            if ( pattern.getFactPattern() != null ) {
                generateFactPattern( pattern.getFactPattern() );
            }
            buf.append( " from " );
            renderExpression( pattern.getExpression() );
            buf.append( "\n" );
        }

        public void visitFromCollectCompositeFactPattern(FromCollectCompositeFactPattern pattern) {
            buf.append( indentation );
            if ( isDSLEnhanced ) {
                // adding passthrough markup
                buf.append( ">" );
            }
            if ( pattern.getFactPattern() != null ) {
                generateFactPattern( pattern.getFactPattern() );
            }
            buf.append( " from collect ( " );
            if ( pattern.getRightPattern() != null ) {
                if ( pattern.getRightPattern() instanceof FactPattern ) {
                    generateFactPattern( (FactPattern) pattern.getRightPattern() );
                } else if ( pattern.getRightPattern() instanceof FromAccumulateCompositeFactPattern ) {
                    visitFromAccumulateCompositeFactPattern( (FromAccumulateCompositeFactPattern) pattern.getRightPattern() );
                } else if ( pattern.getRightPattern() instanceof FromCollectCompositeFactPattern ) {
                    visitFromCollectCompositeFactPattern( (FromCollectCompositeFactPattern) pattern.getRightPattern() );
                } else if ( pattern.getRightPattern() instanceof FromCompositeFactPattern ) {
                    visitFromCompositeFactPattern( (FromCompositeFactPattern) pattern.getRightPattern() );
                } else if ( pattern.getRightPattern() instanceof FreeFormLine ) {
                    visitFreeFormLine( (FreeFormLine) pattern.getRightPattern() );
                } else {
                    throw new IllegalArgumentException( "Unsupported pattern " + pattern.getRightPattern() + " for FROM COLLECT" );
                }
            }
            buf.append( ") \n" );
        }

        public void visitFromAccumulateCompositeFactPattern(FromAccumulateCompositeFactPattern pattern) {
            buf.append( indentation );
            if ( isDSLEnhanced ) {
                // adding passthrough markup
                buf.append( ">" );
            }
            if ( pattern.getFactPattern() != null ) {
                generateFactPattern( pattern.getFactPattern() );
            }
            buf.append( " from accumulate ( " );
            if ( pattern.getSourcePattern() != null ) {
                if ( pattern.getSourcePattern() instanceof FactPattern ) {
                    generateFactPattern( (FactPattern) pattern.getSourcePattern() );
                } else if ( pattern.getSourcePattern() instanceof FromAccumulateCompositeFactPattern ) {
                    visitFromAccumulateCompositeFactPattern( (FromAccumulateCompositeFactPattern) pattern.getSourcePattern() );
                } else if ( pattern.getSourcePattern() instanceof FromCollectCompositeFactPattern ) {
                    visitFromCollectCompositeFactPattern( (FromCollectCompositeFactPattern) pattern.getSourcePattern() );
                } else if ( pattern.getSourcePattern() instanceof FromCompositeFactPattern ) {
                    visitFromCompositeFactPattern( (FromCompositeFactPattern) pattern.getSourcePattern() );
                } else {
                    throw new IllegalArgumentException( "Unsupported pattern " + pattern.getSourcePattern() + " for FROM ACCUMULATE" );
                }
            }
            buf.append( ",\n" );

            if ( pattern.useFunctionOrCode().equals( FromAccumulateCompositeFactPattern.USE_FUNCTION ) ) {
                buf.append( indentation + "\t" );
                buf.append( pattern.getFunction() );
            } else {
                buf.append( indentation + "\tinit( " );
                buf.append( pattern.getInitCode() );
                buf.append( " ),\n" );
                buf.append( indentation + "\taction( " );
                buf.append( pattern.getActionCode() );
                buf.append( " ),\n" );
                if ( pattern.getReverseCode() != null && !pattern.getReverseCode().trim().equals( "" ) ) {
                    buf.append( indentation + "\treverse( " );
                    buf.append( pattern.getReverseCode() );
                    buf.append( " ),\n" );
                }
                buf.append( indentation + "\tresult( " );
                buf.append( pattern.getResultCode() );
                buf.append( " )\n" );
            }
            buf.append( ") \n" );

        }

        public void visitFromEntryPointFactPattern(FromEntryPointFactPattern pattern) {
            buf.append( indentation );
            if ( isDSLEnhanced ) {
                // adding passthrough markup
                buf.append( ">" );
            }
            if ( pattern.getFactPattern() != null ) {
                generateFactPattern( pattern.getFactPattern() );
            }
            buf.append( " from entry-point \"" + pattern.getEntryPointName() + "\"\n" );
        }

        private void renderCompositeFOL(CompositeFactPattern pattern) {
            buf.append( pattern.type );
            if ( pattern.getPatterns() != null ) {
                buf.append( " (" );
                for ( int i = 0; i < pattern.getPatterns().length; i++ ) {
                    renderSubPattern( pattern,
                                      i );
                    if ( i != pattern.getPatterns().length - 1 ) {
                        buf.append( " and " );
                    }
                }
                buf.append( ") \n" );
            }
        }

        private void renderSubPattern(CompositeFactPattern pattern,
                                      int subIndex) {
            if ( pattern.getPatterns() == null || pattern.getPatterns().length == 0 ) {
                return;
            }
            IFactPattern subPattern = pattern.getPatterns()[subIndex];
            if ( subPattern instanceof FactPattern ) {
                this.generateFactPattern( (FactPattern) subPattern );
            } else if ( subPattern instanceof FromAccumulateCompositeFactPattern ) {
                this.visitFromAccumulateCompositeFactPattern( (FromAccumulateCompositeFactPattern) subPattern );
            } else if ( subPattern instanceof FromCollectCompositeFactPattern ) {
                this.visitFromCollectCompositeFactPattern( (FromCollectCompositeFactPattern) subPattern );
            } else if ( subPattern instanceof FromCompositeFactPattern ) {
                this.visitFromCompositeFactPattern( (FromCompositeFactPattern) subPattern );
            } else {
                throw new IllegalStateException( "Unsupported Pattern: " + subPattern.getClass().getName() );
            }
        }

        private void renderExpression(ExpressionFormLine expression) {
            buf.append( expression.getText() );
        }

        public void visitDSLSentence(final DSLSentence sentence) {
            buf.append( indentation );
            buf.append( sentence.interpolate() );
            buf.append( "\n" );
        }

        private void generateFactPattern(FactPattern pattern) {
            if ( pattern.isNegated() ) {
                buf.append( "not " );
            } else if ( pattern.getBoundName() != null ) {
                buf.append( pattern.getBoundName() );
                buf.append( " : " );
            }
            if ( pattern.getFactType() != null ) {
                buf.append( pattern.getFactType() );
            }
            buf.append( "( " );

            // top level constraints
            if ( pattern.constraintList != null ) {
                generateConstraints( pattern );
            }
            buf.append( ")" );

            //Add CEP window definition
            CEPWindow window = pattern.getWindow();
            if ( window.isDefined() ) {
                buf.append( " " );
                buf.append( window.getOperator() );
                buf.append( buildOperatorParameterDRL( window.getParameters() ) );
            }
        }

        private void generateConstraints(FactPattern pattern) {
            int printedCount = 0;
            for ( int i = 0; i < pattern.getFieldConstraints().length; i++ ) {
                StringBuilder buffer = new StringBuilder();
                generateConstraint( pattern.constraintList.constraints[i],
                                    false,
                                    buffer );
                if ( buffer.length() > 0 ) {
                    if ( printedCount > 0 ) {
                        buf.append( ", " );
                    }
                    buf.append( buffer );
                    printedCount++;
                }
            }
        }

        /**
         * Recursively process the nested constraints. It will only put brackets
         * in for the ones that aren't at top level. This makes for more
         * readable DRL in the most common cases.
         */
        private void generateConstraint(FieldConstraint con,
                                        boolean nested,
                                        StringBuilder buf) {
            if ( con instanceof CompositeFieldConstraint ) {
                CompositeFieldConstraint cfc = (CompositeFieldConstraint) con;
                if ( nested ) {
                    buf.append( "( " );
                }
                FieldConstraint[] nestedConstraints = cfc.constraints;
                if ( nestedConstraints != null ) {
                    for ( int i = 0; i < nestedConstraints.length; i++ ) {
                        generateConstraint( nestedConstraints[i],
                                            true,
                                            buf );
                        if ( i < (nestedConstraints.length - 1) ) {
                            // buf.append(" ) ");
                            buf.append( cfc.compositeJunctionType + " " );
                            // buf.append(" ( ");
                        }
                    }
                }
                if ( nested ) {
                    buf.append( ")" );
                }
            } else {
                generateSingleFieldConstraint( (SingleFieldConstraint) con,
                                               buf );
            }
        }

        private void generateSingleFieldConstraint(final SingleFieldConstraint constr,
                                                   StringBuilder buf) {
            if ( constr.getConstraintValueType() == BaseSingleFieldConstraint.TYPE_PREDICATE ) {
                buf.append( "eval( " );
                buf.append( constr.getValue() );
                buf.append( " )" );
            } else {
                if ( constr.getFieldBinding() != null ) {
                    buf.append( constr.getFieldBinding() );
                    buf.append( " : " );
                }
                if ( (constr.getOperator() != null
                        && (constr.getValue() != null
                                || constr.getOperator().equals( "== null" )
                                || constr.getOperator().equals( "!= null" )))
                        || constr.getFieldBinding() != null
                        || constr.getConstraintValueType() == BaseSingleFieldConstraint.TYPE_EXPR_BUILDER_VALUE
                        || constr instanceof SingleFieldConstraintEBLeftSide ) {
                    SingleFieldConstraint parent = (SingleFieldConstraint) constr.getParent();
                    StringBuilder parentBuf = new StringBuilder();
                    while ( parent != null ) {
                        String fieldName = parent.getFieldName();
                        if( fieldName.contains( "." ) ) {
                            fieldName = fieldName.substring( fieldName.indexOf( "." ) + 1 );
                        }
                        parentBuf.insert( 0,
                                          fieldName + "." );
                        parent = (SingleFieldConstraint) parent.getParent();
                    }
                    buf.append( parentBuf );
                    if ( constr instanceof SingleFieldConstraintEBLeftSide ) {
                        buf.append( ((SingleFieldConstraintEBLeftSide) constr).getExpressionLeftSide().getText() );
                    } else {
                        String fieldName = constr.getFieldName();
                        if( fieldName.contains( "." ) ) {
                            fieldName = fieldName.substring( fieldName.indexOf( "." ) + 1 );
                        }
                        buf.append( fieldName );
                    }
                }

                Map<String, String> parameters = null;
                if ( constr instanceof HasParameterizedOperator ) {
                    HasParameterizedOperator hop = (HasParameterizedOperator) constr;
                    parameters = hop.getParameters();
                }

                if ( constr instanceof SingleFieldConstraintEBLeftSide ) {
                    SingleFieldConstraintEBLeftSide sfexp = (SingleFieldConstraintEBLeftSide) constr;
                    addFieldRestriction( buf,
                                         sfexp.getConstraintValueType(),
                                         sfexp.getExpressionLeftSide().getGenericType(),
                                         sfexp.getOperator(),
                                         parameters,
                                         sfexp.getValue(),
                                         sfexp.getExpressionValue() );
                } else {
                    addFieldRestriction( buf,
                                         constr.getConstraintValueType(),
                                         constr.getFieldType(),
                                         constr.getOperator(),
                                         parameters,
                                         constr.getValue(),
                                         constr.getExpressionValue() );
                }

                // and now do the connectives.
                if ( constr.connectives != null ) {
                    for ( int j = 0; j < constr.connectives.length; j++ ) {
                        final ConnectiveConstraint conn = constr.connectives[j];

                        parameters = null;
                        if ( conn instanceof HasParameterizedOperator ) {
                            HasParameterizedOperator hop = (HasParameterizedOperator) conn;
                            parameters = hop.getParameters();
                        }

                        addFieldRestriction( buf,
                                             conn.getConstraintValueType(),
                                             conn.getFieldType(),
                                             conn.getOperator(),
                                             parameters,
                                             conn.getValue(),
                                             conn.getExpressionValue() );
                    }
                }

            }
        }

        private void addFieldRestriction(final StringBuilder buf,
                                         final int type,
                                         final String fieldType,
                                         final String operator,
                                         final Map<String, String> parameters,
                                         final String value,
                                         final ExpressionFormLine expression) {
            if ( operator == null ) {
                return;
            }

            buf.append( " " );
            buf.append( operator );

            if ( parameters != null && parameters.size() > 0 ) {
                buf.append( buildOperatorParameterDRL( parameters ) );
            }

            switch ( type ) {
                case BaseSingleFieldConstraint.TYPE_RET_VALUE :
                    buf.append( " " );
                    buf.append( "( " );
                    buf.append( value );
                    buf.append( " )" );
                    break;
                case BaseSingleFieldConstraint.TYPE_LITERAL :
                    if ( operator.equals( "in" ) || operator.equals( "not in" ) ) {
                        buf.append( " " );
                        buf.append( value );
                    } else {
                        if ( !operator.equals( "== null" ) && !operator.equals( "!= null" ) ) {
                            buf.append( " " );
                            DRLConstraintValueBuilder.buildLHSFieldValue( buf,
                                                                          type,
                                                                          fieldType,
                                                                          value );
                        }
                    }
                    break;
                case BaseSingleFieldConstraint.TYPE_EXPR_BUILDER_VALUE :
                    if ( expression != null ) {
                        buf.append( " " );
                        buf.append( expression.getText() );
                    }
                    break;
                case BaseSingleFieldConstraint.TYPE_TEMPLATE :
                    buf.append( " " );
                    DRLConstraintValueBuilder.buildLHSFieldValue( buf,
                                                                  type,
                                                                  fieldType,
                                                                  "@{" + value + "}" );
                    break;
                case BaseSingleFieldConstraint.TYPE_ENUM :
                    buf.append( " " );
                    DRLConstraintValueBuilder.buildLHSFieldValue( buf,
                                                                  type,
                                                                  fieldType,
                                                                  value );
                    break;
                default :
                    if ( !operator.equals( "== null" ) && !operator.equals( "!= null" ) ) {
                        buf.append( " " );
                        buf.append( value );
                    }
            }
            buf.append( " " );
        }

        private StringBuilder buildOperatorParameterDRL(Map<String, String> parameters) {
            String className = parameters.get( SharedConstants.OPERATOR_PARAMETER_GENERATOR );
            if ( className == null ) {
                throw new IllegalStateException( "Implementation of 'org.drools.ide.common.server.util.OperatorParameterDRLBuilder' undefined. Unable to build Operator Parameter DRL." );
            }

            try {
                OperatorParameterDRLBuilder builder = (OperatorParameterDRLBuilder) Class.forName( className ).newInstance();
                return builder.buildDRL( parameters );
            } catch ( ClassNotFoundException cnfe ) {
                cnfe.fillInStackTrace();
                cnfe.printStackTrace( System.err );
                throw new IllegalStateException( "Unable to generate Operator DRL using class '" + className + "'." );
            } catch ( IllegalAccessException iae ) {
                iae.fillInStackTrace();
                iae.printStackTrace( System.err );
                throw new IllegalStateException( "Unable to generate Operator DRL using class '" + className + "'." );
            } catch ( InstantiationException ie ) {
                ie.fillInStackTrace();
                ie.printStackTrace( System.err );
                throw new IllegalStateException( "Unable to generate Operator DRL using class '" + className + "'." );
            }

        }

    }

    public static class RHSActionVisitor extends ReflectiveVisitor {

        private StringBuilder buf;
        private boolean       isDSLEnhanced;
        private String        indentation;
        private int           idx = 0;

        public RHSActionVisitor(boolean isDSLEnhanced,
                                StringBuilder b,
                                String indentation) {
            this.isDSLEnhanced = isDSLEnhanced;
            this.indentation = indentation;
            buf = b;
        }

        public void visitActionInsertFact(final ActionInsertFact action) {
            this.generateInsertCall( action,
                                     false );
        }

        public void visitActionInsertLogicalFact(final ActionInsertLogicalFact action) {
            this.generateInsertCall( action,
                                     true );
        }

        public void visitFreeFormLine(FreeFormLine ffl) {

            this.buf.append( indentation );
            if ( isDSLEnhanced ) {
                buf.append( ">" );
            }
            this.buf.append( ffl.text );
            this.buf.append( "\n" );
        }

        private void generateInsertCall(final ActionInsertFact action,
                                        final boolean isLogic) {
            buf.append( indentation );
            if ( isDSLEnhanced ) {
                buf.append( ">" );
            }
            if ( action.fieldValues.length == 0 && action.getBoundName() == null ) {
                buf.append( (isLogic) ? "insertLogical( new " : "insert( new " );

                buf.append( action.factType );
                buf.append( "() );\n" );
            } else {
                buf.append( action.factType );
                if ( action.getBoundName() == null ) {
                    buf.append( " fact" );
                    buf.append( idx );
                } else {
                    buf.append( " " + action.getBoundName() );
                }
                buf.append( " = new " );
                buf.append( action.factType );
                buf.append( "();\n" );
                if ( action.getBoundName() == null ) {
                    generateSetMethodCalls( "fact" + idx,
                                            action.fieldValues );
                } else {
                    generateSetMethodCalls( action.getBoundName(),
                                            action.fieldValues );
                }

                buf.append( indentation );
                if ( isDSLEnhanced ) {
                    buf.append( ">" );
                }
                if ( isLogic ) {
                    buf.append( "insertLogical(" );
                    if ( action.getBoundName() == null ) {
                        buf.append( "fact" );
                        buf.append( idx++ );
                    } else {
                        buf.append( action.getBoundName() );
                    }
                    buf.append( " );\n" );
                } else {
                    buf.append( "insert(" );
                    if ( action.getBoundName() == null ) {
                        buf.append( "fact" );
                        buf.append( idx++ );
                    } else {
                        buf.append( action.getBoundName() );
                    }

                    buf.append( " );\n" );
                }
                //                buf.append(idx++);
                //                buf.append(" );\n");
            }
        }

        public void visitActionUpdateField(final ActionUpdateField action) {
            this.visitActionSetField( action );
            buf.append( indentation );
            if ( isDSLEnhanced ) {
                buf.append( ">" );
            }
            buf.append( "update( " );
            buf.append( action.variable );
            buf.append( " );\n" );
        }

        public void visitActionGlobalCollectionAdd(final ActionGlobalCollectionAdd add) {
            buf.append( indentation );
            if ( isDSLEnhanced ) {
                buf.append( ">" );
            }
            buf.append( add.globalName + ".add(" + add.factName + ");\n" );
        }

        public void visitActionRetractFact(final ActionRetractFact action) {
            buf.append( indentation );
            if ( isDSLEnhanced ) {
                buf.append( ">" );
            }
            buf.append( "retract( " );
            buf.append( action.variableName );
            buf.append( " );\n" );
        }

        public void visitDSLSentence(final DSLSentence sentence) {
            buf.append( indentation );
            buf.append( sentence.interpolate() );
            buf.append( "\n" );
        }

        public void visitActionSetField(final ActionSetField action) {
            if ( action instanceof ActionCallMethod ) {
                this.generateSetMethodCallsMethod( (ActionCallMethod) action,
                                                   action.fieldValues );
            } else {
                this.generateSetMethodCalls( action.variable,
                                             action.fieldValues );
            }
        }

        private void generateSetMethodCalls(final String variableName,
                                            final ActionFieldValue[] fieldValues) {
            for ( int i = 0; i < fieldValues.length; i++ ) {
                buf.append( indentation );
                if ( isDSLEnhanced ) {
                    buf.append( ">" );
                }
                buf.append( variableName );

                ActionFieldValue value = fieldValues[i];
                if ( value instanceof ActionFieldFunction ) {
                    buf.append( "." );
                    buf.append( value.field );
                } else {
                    buf.append( ".set" );
                    buf.append( Character.toUpperCase( fieldValues[i].field.charAt( 0 ) ) );
                    buf.append( fieldValues[i].field.substring( 1 ) );
                }
                buf.append( "( " );
                if ( fieldValues[i].isFormula() ) {
                    buf.append( fieldValues[i].value.substring( 1 ) );
                } else if ( fieldValues[i].nature == FieldNature.TYPE_TEMPLATE ) {
                    DRLConstraintValueBuilder.buildRHSFieldValue( buf,
                                                                  fieldValues[i].type,
                                                                  "@{" + fieldValues[i].value + "}" );
                } else {
                    DRLConstraintValueBuilder.buildRHSFieldValue( buf,
                                                                  fieldValues[i].type,
                                                                  fieldValues[i].value );
                }
                buf.append( " );\n" );
            }
        }

        private void generateSetMethodCallsMethod(final ActionCallMethod action,
                                                  final FieldNature[] fieldValues) {
            buf.append( indentation );
            if ( isDSLEnhanced ) {
                buf.append( ">" );
            }
            buf.append( action.variable );
            buf.append( "." );

            buf.append( action.methodName );

            buf.append( "(" );
            boolean isFirst = true;
            for ( int i = 0; i < fieldValues.length; i++ ) {
                ActionFieldFunction valueFunction = (ActionFieldFunction) fieldValues[i];
                if ( isFirst == true ) {
                    isFirst = false;
                } else {
                    buf.append( "," );
                }

                buf.append( valueFunction.value );
            }
            buf.append( " );\n" );

        }
    }

    public static class RHSClassDependencyVisitor extends ReflectiveVisitor {

        private Map<String, List<ActionFieldValue>> classes = new HashMap<String, List<ActionFieldValue>>();

        public void visitFreeFormLine(FreeFormLine ffl) {
            //Do nothing other than preventing ReflectiveVisitor recording an error
        }

        public void visitActionGlobalCollectionAdd(final ActionGlobalCollectionAdd add) {
            //Do nothing other than preventing ReflectiveVisitor recording an error
        }

        public void visitActionRetractFact(final ActionRetractFact action) {
            //Do nothing other than preventing ReflectiveVisitor recording an error
        }

        public void visitDSLSentence(final DSLSentence sentence) {
            //Do nothing other than preventing ReflectiveVisitor recording an error
        }

        public void visitActionInsertFact(final ActionInsertFact action) {
            getClasses( action.fieldValues );
        }

        public void visitActionInsertLogicalFact(final ActionInsertLogicalFact action) {
            getClasses( action.fieldValues );
        }

        public void visitActionUpdateField(final ActionUpdateField action) {
            getClasses( action.fieldValues );
        }

        public void visitActionSetField(final ActionSetField action) {
            getClasses( action.fieldValues );
        }

        public Map<String, List<ActionFieldValue>> getRHSClasses() {
            return classes;
        }

        private void getClasses(ActionFieldValue[] fieldValues) {
            for ( ActionFieldValue afv : fieldValues ) {
                String type = afv.getType();
                List<ActionFieldValue> afvs = classes.get( type );
                if ( afvs == null ) {
                    afvs = new ArrayList<ActionFieldValue>();
                    classes.put( type,
                                 afvs );
                }
                afvs.add( afv );
            }
        }

    }

}
