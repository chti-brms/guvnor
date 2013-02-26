/*
 * Copyright 2012 JBoss Inc
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

package org.kie.guvnor.testscenario.client;

import org.drools.ide.common.client.modeldriven.testing.*;
import org.kie.guvnor.datamodel.model.DropDownData;
import org.kie.guvnor.datamodel.model.ModelField;
import org.kie.guvnor.datamodel.oracle.DataModelOracle;
import org.kie.guvnor.testscenario.model.CollectionFieldData;
import org.kie.guvnor.testscenario.model.ExecutionTrace;
import org.kie.guvnor.testscenario.model.Fact;
import org.kie.guvnor.testscenario.model.FactData;
import org.kie.guvnor.testscenario.model.Field;
import org.kie.guvnor.testscenario.model.FieldData;
import org.kie.guvnor.testscenario.model.Scenario;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FieldConstraintHelper {

    private final Scenario scenario;
    private final ExecutionTrace executionTrace;
    private final DataModelOracle dmo;
    private final String factType;
    private Field field;
    private final Fact fact;

    private boolean parentIsAList = false;

    public FieldConstraintHelper(Scenario scenario,
                                 ExecutionTrace executionTrace,
                                 DataModelOracle dmo,
                                 String factType,
                                 Field field,
                                 Fact fact) {
        this.scenario = scenario;
        this.executionTrace = executionTrace;
        this.dmo = dmo;
        this.factType = factType;
        this.field = field;
        this.fact = fact;
    }

    boolean isThereABoundVariableToSet() {
        List<?> vars = scenario.getFactNamesInScope(executionTrace, true);

        if (vars.size() > 0) {
            for (int i = 0; i < vars.size(); i++) {
                if (scenario.getFactTypes().get(vars.get(i)).getType().equals(resolveFieldType())) {
                    return true;
                }
            }
        }

        return false;
    }

    String resolveFieldType() {
        ModelField modelField = dmo.getField(factType, field.getName());

        if (modelField == null) {
            return null;
        } else if (modelField.getType().equals("Collection")) {
            return dmo.getParametricFieldType(
                    factType,
                    field.getName());
        } else {
            return modelField.getType();
        }
    }

    boolean isItAList() {
        String fieldType = dmo.getFieldType(factType, field.getName());

        if (fieldType != null && fieldType.equals("Collection")) {
            return true;
        }

        return false;
    }

    List<String> getFactNamesInScope() {
        return this.scenario.getFactNamesInScope(this.executionTrace, true);
    }

    FactData getFactTypeByVariableName(String var) {
        return this.scenario.getFactTypes().get(var);
    }


    String getFullFieldName() {
        return factType + "." + field.getName();
    }

    DropDownData getEnums() {
        Map<String, String> currentValueMap = new HashMap<String, String>();
        for (Field f : fact.getFieldData()) {
            if (f instanceof FieldData) {
                FieldData otherFieldData = (FieldData) f;
                currentValueMap.put(otherFieldData.getName(),
                        otherFieldData.getValue());
            }
        }
        return dmo.getEnums(
                factType,
                field.getName(),
                currentValueMap);
    }

    String getFieldType() {
        return dmo.getFieldType(getFullFieldName());
    }

    public FieldDataConstraintEditor createFieldDataConstraintEditor(final FieldData fieldData) {
        return new FieldDataConstraintEditor(
                factType,
                fieldData,
                fact,
                dmo,
                scenario,
                executionTrace);
    }

    public void replaceFieldWith(Field newField) {
        boolean notCollection = true;
        for (Field factsField : fact.getFieldData()) {
            if (factsField instanceof CollectionFieldData) {
                CollectionFieldData fData = (CollectionFieldData) factsField;

                notCollection = false;
                List<FieldData> list = fData.getCollectionFieldList();
                boolean aNewItem = true;
                for (FieldData aField : list) {
                    if (aField.getNature() == 0) {
                        aNewItem = false;
                        aField.setNature(((FieldData) newField).getNature());
                    }
                }
                if (aNewItem) {
                    list.set(list.indexOf(field), (FieldData) newField);
                }
            }

        }
        if (notCollection) {
            fact.getFieldData().set(
                    fact.getFieldData().indexOf(field),
                    newField);
            field = newField;
        }
    }

    public boolean isDependentEnum(FieldConstraintHelper child) {
        if (!fact.getType().equals(child.fact.getType())) {
            return false;
        }
        return dmo.isDependentEnum(fact.getType(), field.getName(), child.field.getName());
    }

    public boolean isTheParentAList() {
        return parentIsAList;
    }

    public void setParentIsAList(boolean parentIsAList) {
        this.parentIsAList = parentIsAList;
    }


}