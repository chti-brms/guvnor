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
package org.drools.guvnor.server;

import static org.drools.guvnor.server.util.ClassicDRLImporter.getRuleName;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.drools.RuleBase;
import org.drools.RuleBaseConfiguration;
import org.drools.RuleBaseFactory;
import org.drools.common.DroolsObjectOutputStream;
import org.drools.compiler.DroolsParserException;
import org.drools.core.util.BinaryRuleBaseLoader;
import org.drools.guvnor.client.rpc.BuilderResult;
import org.drools.guvnor.client.rpc.DetailedSerializationException;
import org.drools.guvnor.client.rpc.PackageConfigData;
import org.drools.guvnor.client.rpc.SnapshotComparisonPageRequest;
import org.drools.guvnor.client.rpc.SnapshotComparisonPageResponse;
import org.drools.guvnor.client.rpc.SnapshotComparisonPageRow;
import org.drools.guvnor.client.rpc.SnapshotDiff;
import org.drools.guvnor.client.rpc.SnapshotDiffs;
import org.drools.guvnor.client.rpc.ValidatedResponse;
import org.drools.guvnor.server.builder.PackageAssembler;
import org.drools.guvnor.server.builder.PackageAssemblerConfiguration;
import org.drools.guvnor.server.builder.PackageDRLAssembler;
import org.drools.guvnor.server.builder.pagerow.SnapshotComparisonPageRowBuilder;
import org.drools.guvnor.server.cache.RuleBaseCache;
import org.drools.guvnor.server.security.RoleTypes;
import org.drools.guvnor.server.util.BRMSSuggestionCompletionLoader;
import org.drools.guvnor.server.util.BuilderResultHelper;
import org.drools.guvnor.server.util.DroolsHeader;
import org.drools.guvnor.server.util.LoggingHelper;
import org.drools.guvnor.server.util.PackageConfigDataFactory;
import org.drools.repository.AssetItem;
import org.drools.repository.PackageItem;
import org.drools.repository.PackageIterator;
import org.drools.repository.RepositoryFilter;
import org.drools.repository.RulesRepository;
import org.drools.repository.RulesRepositoryException;
import org.jboss.seam.annotations.AutoCreate;
import org.jboss.seam.annotations.Name;

import com.google.gwt.user.client.rpc.SerializationException;

/**
 * Handles operations for packages
 */
@Name("org.drools.guvnor.server.RepositoryPackageOperations")
@AutoCreate
public class RepositoryPackageOperations {

    /**
     * Maximum number of rules to display in "list rules in package" method
     */
    private static final int MAX_RULES_TO_SHOW_IN_PACKAGE_LIST = 5000;

    private RulesRepository repository;

    private static final LoggingHelper log = LoggingHelper
            .getLogger(RepositoryPackageOperations.class);

    public void setRulesRepository(RulesRepository repository) {
        this.repository = repository;
    }

    public RulesRepository getRulesRepository() {
        return repository;
    }

    protected PackageConfigData[] listPackages(boolean archive,
                                               String workspace,
                                               RepositoryFilter filter) {
        List<PackageConfigData> result = new ArrayList<PackageConfigData>();
        PackageIterator pkgs = getRulesRepository().listPackages();
        handleIteratePackages(archive,
                workspace,
                filter,
                result,
                pkgs);

        sortPackages(result);
        return result.toArray(new PackageConfigData[result.size()]);
    }

    private void handleIteratePackages(boolean archive,
                                       String workspace,
                                       RepositoryFilter filter,
                                       List<PackageConfigData> result,
                                       PackageIterator pkgs) {
        pkgs.setArchivedIterator( archive );
        while ( pkgs.hasNext() ) {
            PackageItem pkg = pkgs.next();

            PackageConfigData data = new PackageConfigData();
            data.uuid = pkg.getUUID();
            data.name = pkg.getName();
            data.archived = pkg.isArchived();
            data.workspaces = pkg.getWorkspaces();
            handleIsPackagesListed( archive,
                                    workspace,
                                    filter,
                                    result,
                                    data );

            data.subPackages = listSubPackages( pkg,
                                                archive,
                                                null,
                                                filter );

        }
    }

    private PackageConfigData[] listSubPackages(PackageItem parentPkg,
                                                boolean archive,
                                                String workspace,
                                                RepositoryFilter filter) {
        List<PackageConfigData> children = new LinkedList<PackageConfigData>();

        PackageIterator pkgs = parentPkg.listSubPackages();
        handleIteratePackages( archive,
                               workspace,
                               filter,
                               children,
                               pkgs );

        sortPackages(children);
        return children.toArray(new PackageConfigData[children.size()]);
    }

    void sortPackages(List<PackageConfigData> result) {
        Collections.sort(result,
                new Comparator<PackageConfigData>() {

                              public int compare(final PackageConfigData d1,
                                                 final PackageConfigData d2) {
                                  return d1.name.compareTo( d2.name );
                              }

                });
    }

    private void handleIsPackagesListed(boolean archive,
                                        String workspace,
                                        RepositoryFilter filter,
                                        List<PackageConfigData> result,
                                        PackageConfigData data) {
        if ( !archive && (filter == null || filter.accept( data,
                                                           RoleTypes.PACKAGE_READONLY )) && (workspace == null || isWorkspace( workspace,
                                                                                                                               data.workspaces )) ) {
            result.add( data );
        } else if ( archive && data.archived && (filter == null || filter.accept( data,
                                                                                  RoleTypes.PACKAGE_READONLY )) && (workspace == null || isWorkspace( workspace,
                                                                                                                                                      data.workspaces )) ) {
            result.add( data );
        }
    }

    private boolean isWorkspace(String workspace,
                                String[] workspaces) {
        for ( String w : workspaces ) {
            if ( w.equals( workspace ) ) {
                return true;
            }
        }
        return false;
    }

    protected PackageConfigData loadGlobalPackage() {
        PackageItem item = getRulesRepository().loadGlobalArea();

        PackageConfigData data = PackageConfigDataFactory.createPackageConfigDataWithOutDependencies(item);

        if ( data.isSnapshot ) {
            data.snapshotName = item.getSnapshotName();
        }

        return data;
    }

    protected String copyPackage(String sourcePackageName,
                               String destPackageName) throws SerializationException {

        try {
            log.info("USER:" + getCurrentUserName() + " COPYING package [" + sourcePackageName + "] to  package [" + destPackageName + "]");

            return getRulesRepository().copyPackage( sourcePackageName,
                                              destPackageName );
        } catch ( RulesRepositoryException e ) {
            log.error( "Unable to copy package.",
                       e );
            throw e;
        }

        // If we allow package owner to copy package, we will have to update the
        // permission store
        // for the newly copied package.
        // Update permission store
        /*
         * String copiedUuid = ""; try { PackageItem source =
         * repository.loadPackage( destPackageName ); copiedUuid =
         * source.getUUID(); } catch (RulesRepositoryException e) { log.error( e
         * ); } PackageBasedPermissionStore pbps = new
         * PackageBasedPermissionStore(); pbps.addPackageBasedPermission(new
         * PackageBasedPermission(copiedUuid,
         * Identity.instance().getPrincipal().getName(),
         * RoleTypes.PACKAGE_ADMIN));
         */
    }

    protected void removePackage(String uuid) {

        try {
            PackageItem item = getRulesRepository().loadPackageByUUID(uuid);
            log.info("USER:" + getCurrentUserName() + " REMOVEING package [" + item.getName() + "]");
            item.remove();
            getRulesRepository().save();
        } catch (RulesRepositoryException e) {
            log.error("Unable to remove package.",
                    e);
            throw e;
        }
    }

    protected String renamePackage(String uuid,
                                   String newName) {
        log.info("USER:" + getCurrentUserName() + " RENAMING package [UUID: " + uuid + "] to package [" + newName + "]");

        return getRulesRepository().renamePackage(uuid,
                newName);
    }

    protected byte[] exportPackages(String packageName) {
        log.info("USER:" + getCurrentUserName() + " export package [name: " + packageName + "] ");

        byte[] result = null;

        try {
            result = getRulesRepository().dumpPackageFromRepositoryXml( packageName );
        } catch ( PathNotFoundException e ) {
            throw new RulesRepositoryException( e );
        } catch ( IOException e ) {
            throw new RulesRepositoryException( e );
        } catch ( RepositoryException e ) {
            throw new RulesRepositoryException( e );
        }
        return result;
    }

    // TODO: Not working. GUVNOR-475
    protected void importPackages(byte[] byteArray,
                                  boolean importAsNew) {
        getRulesRepository().importPackageToRepository(byteArray,
                importAsNew);
    }

    protected String createPackage(String name,
                                   String description,
                                   String[] workspace) throws RulesRepositoryException {

        log.info("USER: " + getCurrentUserName() + " CREATING package [" + name + "]");
        PackageItem item = getRulesRepository().createPackage(name,
                description,
                workspace);

        return item.getUUID();
    }

    protected String createSubPackage(String name,
                                      String description,
                                      String parentNode) throws SerializationException {
        log.info("USER: " + getCurrentUserName() + " CREATING subPackage [" + name + "], parent [" + parentNode + "]");
        PackageItem item = getRulesRepository().createSubPackage(name,
                description,
                parentNode);
        return item.getUUID();
    }

    protected PackageConfigData loadPackageConfig(PackageItem packageItem) {
        PackageConfigData data = PackageConfigDataFactory.createPackageConfigDataWithDependencies( packageItem );
        if ( data.isSnapshot ) {
            data.snapshotName = packageItem.getSnapshotName();
        }
        return data;
    }

    public ValidatedResponse validatePackageConfiguration(PackageConfigData data) throws SerializationException {
        log.info( "USER:" + getCurrentUserName() + " validatePackageConfiguration package [" + data.name + "]" );

        PackageItem item = getRulesRepository().loadPackage( data.name );

        RuleBaseCache.getInstance().remove( data.uuid );

        BRMSSuggestionCompletionLoader loader = createBRMSSuggestionCompletionLoader();
        loader.getSuggestionEngine( item,
                                    data.header );

        return validateBRMSSuggestionCompletionLoaderResponse(loader);
    }

    public void savePackage(PackageConfigData data) throws SerializationException {
        log.info( "USER:" + getCurrentUserName() + " SAVING package [" + data.name + "]" );

        PackageItem item = getRulesRepository().loadPackage( data.name );

        // If package is being unarchived.
        boolean unarchived = (!data.archived && item.isArchived());
        Calendar packageLastModified = item.getLastModified();

        DroolsHeader.updateDroolsHeader( data.header,
                                         item );
        updateCategoryRules( data,
                             item );

        item.updateExternalURI( data.externalURI );
        item.updateDescription( data.description );
        item.archiveItem( data.archived );
        item.updateBinaryUpToDate( false );
        RuleBaseCache.getInstance().remove( data.uuid );
        item.checkin( data.description );

        // If package is archived, archive all the assets under it
        if ( data.archived ) {
            handleArchivedForSavePackage( data,
                                          item );
        } else if ( unarchived ) {
            handleUnarchivedForSavePackage( data,
                                            item,
                                            packageLastModified );
        }
    }

    BRMSSuggestionCompletionLoader createBRMSSuggestionCompletionLoader() {
        return new BRMSSuggestionCompletionLoader();
    }

    void updateCategoryRules(PackageConfigData data,
                                     PackageItem item) {
        KeyValueTO keyValueTO = convertMapToCsv( data.catRules );
        item.updateCategoryRules( keyValueTO.getKeys(),
                                  keyValueTO.getValues() );
    }

    // HashMap DOES NOT guarantee order in different iterations!
    private static KeyValueTO convertMapToCsv(final Map map) {
        StringBuilder keysBuilder = new StringBuilder();
        StringBuilder valuesBuilder = new StringBuilder();
        for (Object o : map.entrySet()) {
            Map.Entry entry = (Map.Entry) o;
            if (keysBuilder.length() > 0) {
                keysBuilder.append(",");
            }

            if (valuesBuilder.length() > 0) {
                valuesBuilder.append(",");
            }

            keysBuilder.append(entry.getKey());
            valuesBuilder.append(entry.getValue());
        }
        return new KeyValueTO(keysBuilder.toString(),
                valuesBuilder.toString());
    }

    private static class KeyValueTO {
        private final String keys;
        private final String values;

        public KeyValueTO(final String keys,
                          final String values) {
            this.keys = keys;
            this.values = values;
        }

        public String getKeys() {
            return keys;
        }

        public String getValues() {
            return values;
        }
    }

    void handleArchivedForSavePackage(PackageConfigData data,
                                      PackageItem item) {
        for (Iterator<AssetItem> iter = item.getAssets(); iter.hasNext();) {
            AssetItem assetItem = iter.next();
            if ( !assetItem.isArchived() ) {
                assetItem.archiveItem( true );
                assetItem.checkin( data.description );
            }
        }
    }

    void handleUnarchivedForSavePackage(PackageConfigData data,
                                        PackageItem item,
                                        Calendar packageLastModified) {
        for (Iterator<AssetItem> iter = item.getAssets(); iter.hasNext();) {
            AssetItem assetItem = iter.next();
            // Unarchive the assets archived after the package
            // ( == at the same time that the package was archived)
            if ( assetItem.getLastModified().compareTo( packageLastModified ) >= 0 ) {
                assetItem.archiveItem( false );
                assetItem.checkin( data.description );
            }
        }
    }

    private ValidatedResponse validateBRMSSuggestionCompletionLoaderResponse(BRMSSuggestionCompletionLoader loader) {
        ValidatedResponse res = new ValidatedResponse();
        if (loader.hasErrors()) {
            res.hasErrors = true;
            String err = "";
            for (Iterator iter = loader.getErrors().iterator(); iter.hasNext();) {
                err += (String) iter.next();
                if (iter.hasNext()) err += "\n";
            }
            res.errorHeader = "Package validation errors";
            res.errorMessage = err;
        }
        return res;
    }

    protected void createPackageSnapshot(String packageName,
                                         String snapshotName,
                                         boolean replaceExisting,
                                         String comment) {

        log.info("USER:" + getCurrentUserName() + " CREATING PACKAGE SNAPSHOT for package: [" + packageName + "] snapshot name: [" + snapshotName);

        if (replaceExisting) {
            getRulesRepository().removePackageSnapshot(packageName,
                    snapshotName);
        }

        getRulesRepository().createPackageSnapshot(packageName,
                snapshotName);
        PackageItem item = getRulesRepository().loadPackageSnapshot(packageName,
                snapshotName);
        item.updateCheckinComment(comment);
        getRulesRepository().save();

    }

    protected void copyOrRemoveSnapshot(String packageName,
                                        String snapshotName,
                                        boolean delete,
                                        String newSnapshotName) throws SerializationException {

        if (delete) {
            log.info("USER:" + getCurrentUserName() + " REMOVING SNAPSHOT for package: [" + packageName + "] snapshot: [" + snapshotName + "]");
            getRulesRepository().removePackageSnapshot(packageName,
                    snapshotName);
        } else {
            if (newSnapshotName.equals("")) {
                throw new SerializationException("Need to have a new snapshot name.");
            }
            log.info("USER:" + getCurrentUserName() + " COPYING SNAPSHOT for package: [" + packageName + "] snapshot: [" + snapshotName + "] to [" + newSnapshotName + "]");

            getRulesRepository().copyPackageSnapshot(packageName,
                    snapshotName,
                    newSnapshotName);
        }

    }

    public BuilderResult buildPackage(String packageUUID,
                                      boolean force,
                                      String buildMode,
                                      String statusOperator,
                                      String statusDescriptionValue,
                                      boolean enableStatusSelector,
                                      String categoryOperator,
                                      String category,
                                      boolean enableCategorySelector,
                                      String customSelectorName) throws SerializationException {

        PackageItem item = getRulesRepository().loadPackageByUUID(packageUUID);
        try {
            return buildPackage(item,
                    force,
                    buildMode,
                    statusOperator,
                    statusDescriptionValue,
                    enableStatusSelector,
                    categoryOperator,
                    category,
                    enableCategorySelector,
                    customSelectorName);
        } catch (NoClassDefFoundError e) {
            throw new DetailedSerializationException("Unable to find a class that was needed when building the package  [" + e.getMessage() + "]",
                    "Perhaps you are missing them from the model jars, or from the BRMS itself (lib directory).");
        } catch (UnsupportedClassVersionError e) {
            throw new DetailedSerializationException("Can not build the package. One or more of the classes that are needed were compiled with an unsupported Java version.",
                    "For example the pojo classes were compiled with Java 1.6 and Guvnor is running on Java 1.5. [" + e.getMessage() + "]");
        }
    }

    private BuilderResult buildPackage(PackageItem item,
                                       boolean force,
                                       String buildMode,
                                       String statusOperator,
                                       String statusDescriptionValue,
                                       boolean enableStatusSelector,
                                       String categoryOperator,
                                       String category,
                                       boolean enableCategorySelector,
                                       String selectorConfigName) throws DetailedSerializationException {
        if (!force && item.isBinaryUpToDate()) {
            // we can just return all OK if its up to date.
            return null;
        }
        PackageAssemblerConfiguration packageAssemblerConfiguration = new PackageAssemblerConfiguration();
        packageAssemblerConfiguration.setBuildMode(buildMode);
        packageAssemblerConfiguration.setStatusOperator(statusOperator);
        packageAssemblerConfiguration.setStatusDescriptionValue(statusDescriptionValue);
        packageAssemblerConfiguration.setEnableStatusSelector(enableStatusSelector);
        packageAssemblerConfiguration.setCategoryOperator(categoryOperator);
        packageAssemblerConfiguration.setCategoryValue(category);
        packageAssemblerConfiguration.setEnableCategorySelector(enableCategorySelector);
        packageAssemblerConfiguration.setCustomSelectorConfigName(selectorConfigName);
        PackageAssembler asm = new PackageAssembler(item,
                packageAssemblerConfiguration);
        asm.compile();
        if (asm.hasErrors()) {
            BuilderResult result = new BuilderResult();
            BuilderResultHelper builderResultHelper = new BuilderResultHelper();
            result.setLines(builderResultHelper.generateBuilderResults(asm.getErrors()));
            return result;
        }
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutput out = new DroolsObjectOutputStream(bout);
            out.writeObject(asm.getBinaryPackage());

            item.updateCompiledPackage(new ByteArrayInputStream(bout.toByteArray()));
            out.flush();
            out.close();

            updateBinaryPackage(item,
                    asm);
            getRulesRepository().save();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("An error occurred building the package [" + item.getName() + "]: " + e.getMessage());
            throw new DetailedSerializationException("An error occurred building the package.",
                    e.getMessage());
        }

        return null;
    }

    private void updateBinaryPackage(PackageItem item,
                                     PackageAssembler asm) throws SerializationException {
        item.updateBinaryUpToDate(true);

        // adding the MapBackedClassloader that is the classloader from the
        // rulebase classloader
        final ClassLoader classLoader = asm.getBuilder().getRootClassLoader();
        RuleBase rulebase = RuleBaseFactory.newRuleBase( new RuleBaseConfiguration( classLoader ) );
        BinaryRuleBaseLoader rbl = new BinaryRuleBaseLoader( rulebase,
                                                             classLoader );
        rbl.addPackage( new ByteArrayInputStream( item.getCompiledPackageBytes() ) );
    }

    private String getCurrentUserName() {
        return getRulesRepository().getSession().getUserID();
    }

    protected BuilderResult buildPackage(PackageItem item,
                                         boolean force) throws DetailedSerializationException {
        return buildPackage(item,
                force,
                null,
                null,
                null,
                false,
                null,
                null,
                false,
                null);
    }

    protected String buildPackageSource(String packageUUID) throws SerializationException {

        PackageItem item = getRulesRepository().loadPackageByUUID(packageUUID);
        PackageDRLAssembler asm = new PackageDRLAssembler(item);
        return asm.getDRL();
    }

    protected String[] listRulesInPackage(String packageName) throws SerializationException {
        // load package
        PackageItem item = getRulesRepository().loadPackage(packageName);

        PackageDRLAssembler assembler = createPackageDRLAssembler(item);

        List<String> result = new ArrayList<String>();
        try {

            String drl = assembler.getDRL();
            if ( drl == null || "".equals( drl ) ) {
                return new String[0];
            } else {
                parseRulesToPackageList( assembler,
                                         result );
            }

            return result.toArray(new String[result.size()]);
        } catch (DroolsParserException e) {
            log.error("Unable to list rules in package",
                    e);
            return new String[0];
        }
    }

    PackageDRLAssembler createPackageDRLAssembler(final PackageItem packageItem) {
        return new PackageDRLAssembler(packageItem);
    }

    void parseRulesToPackageList(PackageDRLAssembler asm,
                                 List<String> result) throws DroolsParserException {
        int count = 0;
        StringTokenizer stringTokenizer = new StringTokenizer(asm.getDRL(),
                "\n\r");
        while (stringTokenizer.hasMoreTokens()) {
            String line = stringTokenizer.nextToken().trim();
            if (line.startsWith("rule ")) {
                String name = getRuleName(line);
                result.add(name);
                count++;
                if (count == MAX_RULES_TO_SHOW_IN_PACKAGE_LIST) {
                    result.add("More then " + MAX_RULES_TO_SHOW_IN_PACKAGE_LIST + " rules.");
                    break;
                }
            }
        }
    }

    /**
     * @deprecated in favour of {@link compareSnapshots(SnapshotComparisonPageRequest)}
     */
    protected SnapshotDiffs compareSnapshots(String packageName,
                                             String firstSnapshotName,
                                             String secondSnapshotName) {
        SnapshotDiffs diffs = new SnapshotDiffs();
        List<SnapshotDiff> list = new ArrayList<SnapshotDiff>();

        PackageItem leftPackage = getRulesRepository().loadPackageSnapshot(packageName,
                firstSnapshotName);
        PackageItem rightPackage = getRulesRepository().loadPackageSnapshot(packageName,
                secondSnapshotName);

        // Older one has to be on the left.
        if (isRightOlderThanLeft(leftPackage,
                rightPackage)) {
            PackageItem temp = leftPackage;
            leftPackage = rightPackage;
            rightPackage = temp;

            diffs.leftName = secondSnapshotName;
            diffs.rightName = firstSnapshotName;
        } else {
            diffs.leftName = firstSnapshotName;
            diffs.rightName = secondSnapshotName;
        }

        Iterator<AssetItem> leftExistingIter = leftPackage.getAssets();
        while (leftExistingIter.hasNext()) {
            AssetItem left = leftExistingIter.next();
            if (isPackageItemDeleted(rightPackage,
                    left)) {
                SnapshotDiff diff = new SnapshotDiff();

                diff.name = left.getName();
                diff.diffType = SnapshotDiff.TYPE_DELETED;
                diff.leftUuid = left.getUUID();

                list.add(diff);
            }
        }

        Iterator<AssetItem> rightExistingIter = rightPackage.getAssets();
        while (rightExistingIter.hasNext()) {
            AssetItem right = rightExistingIter.next();
            AssetItem left = null;
            if (right != null && leftPackage.containsAsset(right.getName())) {
                left = leftPackage.loadAsset(right.getName());
            }

            // Asset is deleted or added
            if (right == null || left == null) {
                SnapshotDiff diff = new SnapshotDiff();

                if (left == null) {
                    diff.name = right.getName();
                    diff.diffType = SnapshotDiff.TYPE_ADDED;
                    diff.rightUuid = right.getUUID();
                }

                list.add(diff);
            } else if (isAssetArchivedOrRestored(right,
                    left)) { // Has the asset
                // been archived
                // or restored
                SnapshotDiff diff = new SnapshotDiff();

                diff.name = right.getName();
                diff.leftUuid = left.getUUID();
                diff.rightUuid = right.getUUID();

                if (left.isArchived()) {
                    diff.diffType = SnapshotDiff.TYPE_RESTORED;
                } else {
                    diff.diffType = SnapshotDiff.TYPE_ARCHIVED;
                }

                list.add(diff);
            } else if (isAssetItemUpdated(right,
                    left)) { // Has the asset been
                // updated
                SnapshotDiff diff = new SnapshotDiff();

                diff.name = right.getName();
                diff.leftUuid = left.getUUID();
                diff.rightUuid = right.getUUID();
                diff.diffType = SnapshotDiff.TYPE_UPDATED;

                list.add(diff);
            }
        }

        diffs.diffs = list.toArray(new SnapshotDiff[list.size()]);
        return diffs;
    }

    private boolean isAssetArchivedOrRestored(AssetItem right,
                                              AssetItem left) {
        return right.isArchived() != left.isArchived();
    }

    private boolean isAssetItemUpdated(AssetItem right,
                                       AssetItem left) {
        return right.getLastModified().compareTo(left.getLastModified()) != 0;
    }

    private boolean isPackageItemDeleted(PackageItem rightPackage,
                                         AssetItem left) {
        return !rightPackage.containsAsset(left.getName());
    }

    private boolean isRightOlderThanLeft(PackageItem leftPackage,
                                         PackageItem rightPackage) {
        return leftPackage.getLastModified().compareTo(rightPackage.getLastModified()) > 0;
    }

    protected SnapshotComparisonPageResponse compareSnapshots(SnapshotComparisonPageRequest request) {

        SnapshotComparisonPageResponse response = new SnapshotComparisonPageResponse();

        // Do query (bit of a cheat really!)
        long start = System.currentTimeMillis();
        SnapshotDiffs diffs = compareSnapshots(request.getPackageName(),
                request.getFirstSnapshotName(),
                request.getSecondSnapshotName());
        log.debug("Search time: " + (System.currentTimeMillis() - start));

        // Populate response
        response.setLeftSnapshotName( diffs.leftName );
        response.setRightSnapshotName( diffs.rightName );
        
        SnapshotComparisonPageRowBuilder snapshotComparisonPageRowBuilder = new SnapshotComparisonPageRowBuilder();
        List<SnapshotComparisonPageRow> rowList = snapshotComparisonPageRowBuilder.createRows( request, diffs );
        
        response.setPageRowList( rowList );
        response.setStartRowIndex( request.getStartRowIndex() );
        response.setTotalRowSize( diffs.diffs.length );
        response.setTotalRowSizeExact( true );
        response.setLastPage( (request.getStartRowIndex() + rowList.size() == diffs.diffs.length) );

        long methodDuration = System.currentTimeMillis() - start;
        log.debug("Compared Snapshots ('" + request.getFirstSnapshotName() + "') and ('" + request.getSecondSnapshotName() + "') in package ('" + request.getPackageName() + "') in " + methodDuration + " ms.");

        return response;
    }

}
