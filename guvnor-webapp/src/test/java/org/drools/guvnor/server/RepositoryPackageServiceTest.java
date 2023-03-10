/*
 * Copyright 2011 JBoss Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.drools.guvnor.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import org.drools.Person;
import org.drools.RuleBase;
import org.drools.StatelessSession;
import org.drools.core.util.BinaryRuleBaseLoader;
import org.drools.core.util.DroolsStreamUtils;
import org.drools.guvnor.client.common.AssetFormats;
import org.drools.guvnor.client.explorer.ExplorerNodeConfig;
import org.drools.guvnor.client.rpc.BuilderResult;
import org.drools.guvnor.client.rpc.DetailedSerializationException;
import org.drools.guvnor.client.rpc.PackageConfigData;
import org.drools.guvnor.client.rpc.RepositoryService;
import org.drools.guvnor.client.rpc.RuleAsset;
import org.drools.guvnor.client.rpc.SnapshotComparisonPageRequest;
import org.drools.guvnor.client.rpc.SnapshotComparisonPageResponse;
import org.drools.guvnor.client.rpc.SnapshotComparisonPageRow;
import org.drools.guvnor.client.rpc.SnapshotDiff;
import org.drools.guvnor.client.rpc.SnapshotDiffs;
import org.drools.guvnor.client.rpc.SnapshotInfo;
import org.drools.guvnor.client.rpc.TableDataResult;
import org.drools.guvnor.client.rpc.ValidatedResponse;
import org.drools.guvnor.server.util.DroolsHeader;
import org.drools.ide.common.client.modeldriven.SuggestionCompletionEngine;
import org.drools.ide.common.client.modeldriven.brl.ActionFieldValue;
import org.drools.ide.common.client.modeldriven.brl.ActionSetField;
import org.drools.ide.common.client.modeldriven.brl.BaseSingleFieldConstraint;
import org.drools.ide.common.client.modeldriven.brl.FactPattern;
import org.drools.ide.common.client.modeldriven.brl.PortableObject;
import org.drools.ide.common.client.modeldriven.brl.RuleModel;
import org.drools.ide.common.client.modeldriven.brl.SingleFieldConstraint;
import org.drools.ide.common.server.util.BRXMLPersistence;
import org.drools.repository.AssetItem;
import org.drools.repository.PackageItem;
import org.drools.repository.RulesRepository;
import org.drools.repository.RulesRepositoryException;
import org.drools.rule.Package;
import org.junit.Ignore;
import org.junit.Test;

import com.google.gwt.user.client.rpc.SerializationException;

public class RepositoryPackageServiceTest extends GuvnorTestBase {

    @Test
    public void testSnapshotDiff() throws Exception {
        RepositoryService impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        RepositoryCategoryService repositoryCategoryService = getRepositoryCategoryService();
        // Lets make a package and a rule into tit.
        repositoryCategoryService.createCategory( "/",
                                                  "snapshotDiffTesting",
                                                  "y" );
        String packageUuid = repositoryPackageService.createPackage( "testSnapshotDiff",
                                                                     "d" );

        assertNotNull( packageUuid );

        // Create two rules
        String archiveRuleUuid = impl.createNewRule( "testRuleArchived",
                                                     "",
                                                     "snapshotDiffTesting",
                                                     "testSnapshotDiff",
                                                     AssetFormats.DRL );
        String modifiedRuleUuid = impl.createNewRule( "testRuleModified",
                                                      "",
                                                      "snapshotDiffTesting",
                                                      "testSnapshotDiff",
                                                      AssetFormats.DRL );
        String deletedRuleUuid = impl.createNewRule( "testRuleDeleted",
                                                     "",
                                                     "snapshotDiffTesting",
                                                     "testSnapshotDiff",
                                                     AssetFormats.DRL );
        String restoredRuleUuid = impl.createNewRule( "testRuleRestored",
                                                      "",
                                                      "snapshotDiffTesting",
                                                      "testSnapshotDiff",
                                                      AssetFormats.DRL );
        @SuppressWarnings("unused")
        String noChangesRuleUuid = impl.createNewRule( "testRuleNoChanges",
                                                       "",
                                                       "snapshotDiffTesting",
                                                       "testSnapshotDiff",
                                                       AssetFormats.DRL );
        RepositoryAssetService repositoryAssetService = getRepositoryAssetService();
        repositoryAssetService.archiveAsset( restoredRuleUuid );

        // Create a snapshot called FIRST for the package
        repositoryPackageService.createPackageSnapshot( "testSnapshotDiff",
                                                        "FIRST",
                                                        false,
                                                        "ya" );
        assertEquals( 1,
                      repositoryPackageService.listSnapshots( "testSnapshotDiff" ).length );
        assertEquals( 4,
                      repositoryPackageService.listRulesInPackage( "testSnapshotDiff" ).length );

        // Change the rule, archive one, delete one and create a new one
        RuleAsset asset = repositoryAssetService.loadRuleAsset( modifiedRuleUuid );
        String uuid = repositoryAssetService.checkinVersion( asset );
        assertNotNull( uuid );

        repositoryAssetService.removeAsset( deletedRuleUuid );

        repositoryAssetService.archiveAsset( archiveRuleUuid );

        @SuppressWarnings("unused")
        String addedRuleUuid = impl.createNewRule( "testRuleAdded",
                                                   "",
                                                   "snapshotDiffTesting",
                                                   "testSnapshotDiff",
                                                   AssetFormats.DRL );

        repositoryAssetService.unArchiveAsset( restoredRuleUuid );

        // Create a snapshot called SECOND for the package
        repositoryPackageService.createPackageSnapshot( "testSnapshotDiff",
                                                        "SECOND",
                                                        false,
                                                        "we" );
        assertEquals( 2,
                      repositoryPackageService.listSnapshots( "testSnapshotDiff" ).length );
        assertEquals( 4,
                      repositoryPackageService.listRulesInPackage( "testSnapshotDiff" ).length );

        // Compare the snapshots
        SnapshotDiffs diffs = repositoryPackageService.compareSnapshots( "testSnapshotDiff",
                                                                         "FIRST",
                                                                         "SECOND" );
        assertEquals( "FIRST",
                      diffs.leftName );
        assertEquals( "SECOND",
                      diffs.rightName );

        SnapshotDiff[] list = diffs.diffs;
        assertNotNull( list );
        assertEquals( 5,
                      list.length );

        for ( int i = 0; i < list.length; i++ ) {
            SnapshotDiff diff = list[i];
            if ( diff.name.equals( "testRuleArchived" ) ) {
                assertEquals( SnapshotDiff.TYPE_ARCHIVED,
                              diff.diffType );
                assertNotNull( diff.leftUuid );
                assertNotNull( diff.rightUuid );
            } else if ( diff.name.equals( "testRuleModified" ) ) {
                assertEquals( SnapshotDiff.TYPE_UPDATED,
                              diff.diffType );
                assertNotNull( diff.leftUuid );
                assertNotNull( diff.rightUuid );
            } else if ( diff.name.equals( "testRuleAdded" ) ) {
                assertEquals( SnapshotDiff.TYPE_ADDED,
                              diff.diffType );
                assertNull( diff.leftUuid );
                assertNotNull( diff.rightUuid );
            } else if ( diff.name.equals( "testRuleDeleted" ) ) {
                assertEquals( SnapshotDiff.TYPE_DELETED,
                              diff.diffType );
                assertNotNull( diff.leftUuid );
                assertNull( diff.rightUuid );
            } else if ( diff.name.equals( "testRuleRestored" ) ) {
                assertEquals( SnapshotDiff.TYPE_RESTORED,
                              diff.diffType );
                assertNotNull( diff.leftUuid );
                assertNotNull( diff.rightUuid );
            } else {
                fail( "Diff not expected." );
            }
        }
    }

    @Test
    public void testPackageBinaryUpdatedResetWhenDeletingAnAsset() throws Exception {
        ServiceImplementation serviceImplementation = getServiceImplementation();

        PackageItem packageItem = serviceImplementation.getRulesRepository().createPackage( "testPackageBinaryUpdatedResetWhenDeletingAnAsset",
                                                                                            "" );

        AssetItem assetItem = packageItem.addAsset( "temp",
                                                    "" );

        assertNotNull( packageItem.getName() );
        packageItem.updateBinaryUpToDate( true );
        assertTrue( packageItem.isBinaryUpToDate() );

        serviceImplementation.deleteUncheckedRule( assetItem.getUUID() );

        PackageItem reloadedPackage = serviceImplementation.getRulesRepository().loadPackage( packageItem.getName() );

        assertEquals( packageItem.getName(),
                      reloadedPackage.getName() );
        assertFalse( reloadedPackage.isBinaryUpToDate() );
    }

    @Test
    public void testGetHistoryPackageBinary() throws Exception {
        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        RulesRepository repo = impl.getRulesRepository();

        // create our package
        PackageItem pkg = repo.createPackage( "testGetHistoryPackageBinary",
                                              "" );
        assertFalse( pkg.isBinaryUpToDate() );
        DroolsHeader.updateDroolsHeader( "import org.drools.Person",
                                                  pkg );
        AssetItem rule1 = pkg.addAsset( "rule_1",
                                        "" );
        rule1.updateFormat( AssetFormats.DRL );
        rule1.updateContent( "rule 'rule1' \n when \np : Person() \n then \np.setAge(42); \n end" );
        rule1.checkin( "" );
        repo.save();
        assertFalse( pkg.isBinaryUpToDate() );

        //Create a history package with no compiled binary stored. 
        pkg.checkout();
        pkg.checkin( "version1" );
        //Verify history package binary.
        PackageItem p = repo.loadPackage( "testGetHistoryPackageBinary",
                                          2 );
        assertEquals( "version1",
                      p.getCheckinComment() );
        assertFalse( p.isBinaryUpToDate() );
        byte[] result = p.getCompiledPackageBytes();
        assertNull( result );

        //Build package update package node to store compiled binary. This is wont work for a history version
        //The best strategy we can do is to force a package build before checkin. TODO.
        //BuilderResult results = repositoryPackageService.buildPackage(pkg.getUUID(), true);
    }

    @Test
    public void testDependencyHistoryPackage() throws Exception {
        ServiceImplementation impl = getServiceImplementation();
        //Package version 1
        PackageItem pkg = impl.getRulesRepository().createPackage( "testDependencyHistoryPackage",
                                                                   "" );

        AssetItem func = pkg.addAsset( "func",
                                       "" );
        func.updateFormat( AssetFormats.FUNCTION );
        func.updateContent( "function void foo() { System.out.println(version 1); }" );
        func.checkin( "func version 1" );
        func.updateContent( "function void foo() { System.out.println(version 2); }" );
        func.checkin( "func version 2" );

        //Package version 2     
        pkg.checkout();
        pkg.checkin( "package version 2" );

        //calling updateDependency creates package version 3
        pkg.updateDependency( "func?version=1" );
        pkg.checkin( "package version 3" );

        func.updateContent( "function void foo() { System.out.println(version 2); }" );
        func.checkin( "func version 3" );

        //Package version 4     
        pkg.checkout();
        pkg.checkin( "package version 4" );

        //Verify the latest version
        PackageItem item = impl.getRulesRepository().loadPackage( "testDependencyHistoryPackage" );
        assertEquals( "package version 4",
                      item.getCheckinComment() );
        assertEquals( "func?version=1",
                      item.getDependencies()[0] );

        //Verify version 2
        item = impl.getRulesRepository().loadPackage( "testDependencyHistoryPackage",
                                                      2 );
        assertEquals( "package version 2",
                      item.getCheckinComment() );
        assertEquals( "func?version=2",
                      item.getDependencies()[0] );

        //Verify version 3
        item = impl.getRulesRepository().loadPackage( "testDependencyHistoryPackage",
                                                      3 );
        assertEquals( "package version 3",
                      item.getCheckinComment() );
        assertEquals( "func?version=1",
                      item.getDependencies()[0] );
    }

    @Test
    public void testMovePackage() throws Exception {
        RepositoryService impl = getServiceImplementation();
        RepositoryCategoryService repositoryCategoryService = getRepositoryCategoryService();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        String[] cats = repositoryCategoryService.loadChildCategories( "/" );
        if ( cats.length == 0 ) {
            repositoryCategoryService.createCategory( "/",
                                                      "la",
                                                      "d" );
        }
        String sourcePkgId = repositoryPackageService.createPackage( "sourcePackage",
                                                                     "description" );
        String destPkgId = repositoryPackageService.createPackage( "targetPackage",
                                                                   "description" );

        String cat = repositoryCategoryService.loadChildCategories( "/" )[0];

        String uuid = impl.createNewRule( "testMovePackage",
                                          "desc",
                                          cat,
                                          "sourcePackage",
                                          AssetFormats.DRL );
        RepositoryAssetService repositoryAssetService = getRepositoryAssetService();
        TableDataResult res = repositoryAssetService.listAssets( destPkgId,
                                                                 new String[]{"drl"},
                                                                 0,
                                                                 2,
                                                                 ExplorerNodeConfig.RULE_LIST_TABLE_ID );
        assertEquals( 0,
                      res.data.length );

        repositoryAssetService.changeAssetPackage( uuid,
                                                   "targetPackage",
                                                   "yeah" );
        res = repositoryAssetService.listAssets( destPkgId,
                                                 new String[]{"drl"},
                                                 0,
                                                 2,
                                                 ExplorerNodeConfig.RULE_LIST_TABLE_ID );

        assertEquals( 1,
                      res.data.length );

        res = repositoryAssetService.listAssets( sourcePkgId,
                                                 new String[]{"drl"},
                                                 0,
                                                 2,
                                                 ExplorerNodeConfig.RULE_LIST_TABLE_ID );

        assertEquals( 0,
                      res.data.length );

    }

    @Test
    public void testSnapshot() throws Exception {
        RepositoryService impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        RepositoryCategoryService repositoryCategoryService = getRepositoryCategoryService();
        repositoryCategoryService.createCategory( "/",
                                                  "snapshotTesting",
                                                  "y" );
        repositoryPackageService.createPackage( "testSnapshot",
                                                "d" );
        @SuppressWarnings("unused")
        String uuid = impl.createNewRule( "testSnapshotRule",
                                          "",
                                          "snapshotTesting",
                                          "testSnapshot",
                                          AssetFormats.DRL );

        repositoryPackageService.createPackageSnapshot( "testSnapshot",
                                                        "X",
                                                        false,
                                                        "ya" );
        SnapshotInfo[] snaps = repositoryPackageService.listSnapshots( "testSnapshot" );
        assertEquals( 1,
                      snaps.length );
        assertEquals( "X",
                      snaps[0].name );
        assertEquals( "ya",
                      snaps[0].comment );
        assertNotNull( snaps[0].uuid );
        PackageConfigData confSnap = repositoryPackageService.loadPackageConfig( snaps[0].uuid );
        assertEquals( "testSnapshot",
                      confSnap.name );

        repositoryPackageService.createPackageSnapshot( "testSnapshot",
                                                        "Y",
                                                        false,
                                                        "we" );
        assertEquals( 2,
                      repositoryPackageService.listSnapshots( "testSnapshot" ).length );
        repositoryPackageService.createPackageSnapshot( "testSnapshot",
                                                        "X",
                                                        true,
                                                        "we" );
        assertEquals( 2,
                      repositoryPackageService.listSnapshots( "testSnapshot" ).length );

        repositoryPackageService.copyOrRemoveSnapshot( "testSnapshot",
                                                       "X",
                                                       false,
                                                       "Q" );
        assertEquals( 3,
                      repositoryPackageService.listSnapshots( "testSnapshot" ).length );

        try {
            repositoryPackageService.copyOrRemoveSnapshot( "testSnapshot",
                                                           "X",
                                                           false,
                                                           "" );
            fail( "should not be able to copy snapshot to empty detination" );
        } catch ( SerializationException e ) {
            assertNotNull( e.getMessage() );
        }

        repositoryPackageService.copyOrRemoveSnapshot( "testSnapshot",
                                                       "X",
                                                       true,
                                                       null );
        assertEquals( 2,
                      repositoryPackageService.listSnapshots( "testSnapshot" ).length );

    }

    @Test
    public void testSnapshotRebuild() throws Exception {

        ServiceImplementation impl = getServiceImplementation();
        RulesRepository repo = impl.getRulesRepository();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();

        // get rid of other snapshot crap
        Iterator< ? > pkit = repo.listPackages();
        while ( pkit.hasNext() ) {
            PackageItem pkg = (PackageItem) pkit.next();
            String[] snaps = repo.listPackageSnapshots( pkg.getName() );
            for ( String snapName : snaps ) {
                repo.removePackageSnapshot( pkg.getName(),
                                            snapName );
            }
        }

        final PackageItem pkg = repo.createPackage( "testSnapshotRebuild",
                                                    "" );
        DroolsHeader.updateDroolsHeader( "import java.util.List",
                                                  pkg );
        repo.save();

        AssetItem item = pkg.addAsset( "anAsset",
                                       "" );
        item.updateFormat( AssetFormats.DRL );
        item.updateContent( " rule abc \n when \n then \n System.out.println(42); \n end" );
        item.checkin( "" );

        BuilderResult res = repositoryPackageService.buildPackage( pkg.getUUID(),
                                                                   true );
        assertNull( res );

        repositoryPackageService.createPackageSnapshot( "testSnapshotRebuild",
                                                        "SNAP",
                                                        false,
                                                        "" );

        PackageItem snap = repo.loadPackageSnapshot( "testSnapshotRebuild",
                                                     "SNAP" );
        long snapTime = snap.getLastModified().getTimeInMillis();

        Thread.sleep( 100 );

        repositoryPackageService.rebuildSnapshots();

        PackageItem snap_ = repo.loadPackageSnapshot( "testSnapshotRebuild",
                                                      "SNAP" );
        long newTime = snap_.getLastModified().getTimeInMillis();

        assertTrue( newTime > snapTime );

        item.updateContent( "garbage" );
        item.checkin( "" );

        repositoryPackageService.createPackageSnapshot( "testSnapshotRebuild",
                                                        "SNAP2",
                                                        false,
                                                        "" );

        try {
            repositoryPackageService.rebuildSnapshots();
        } catch ( DetailedSerializationException e ) {
            assertNotNull( e.getMessage() );
            assertNotNull( e.getLongDescription() );
        }

    }

    @Test
    public void testPackageRebuild() throws Exception {

        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();

        RulesRepository repo = impl.getRulesRepository();

        final PackageItem pkg = repo.createPackage( "testPackageRebuild",
                                                    "" );
        DroolsHeader.updateDroolsHeader( "import java.util.List",
                                                  pkg );
        repo.save();

        AssetItem item = pkg.addAsset( "anAsset",
                                       "" );
        item.updateFormat( AssetFormats.DRL );
        item.updateContent( " rule abc \n when \n then \n System.out.println(42); \n end" );
        item.checkin( "" );

        assertNull( pkg.getCompiledPackageBytes() );

        long last = pkg.getLastModified().getTimeInMillis();
        Thread.sleep( 100 );
        try {
            repositoryPackageService.rebuildPackages();
        } catch ( DetailedSerializationException e ) {
            assertNotNull( e.getMessage() );
            assertNotNull( e.getLongDescription() );
        }

        assertFalse( pkg.getLastModified().getTimeInMillis() == last );
        assertNotNull( pkg.getCompiledPackageBytes() );

    }

    @Test
    public void testExportPackage() throws Exception {
        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        RepositoryCategoryService repositoryCategoryService = getRepositoryCategoryService();
        int n = repositoryPackageService.listPackages().length;
        repositoryCategoryService.createCategory( "/",
                                                  "testExportPackageCat1",
                                                  "desc" );
        repositoryCategoryService.createCategory( "/",
                                                  "testExportPackageCat2",
                                                  "desc" );
        PackageItem p = impl.getRulesRepository().createPackage( "testExportPackage",
                                                                 "" );

        String uuid1 = impl.createNewRule( "testExportPackageAsset1",
                                           "desc",
                                           "testExportPackageCat1",
                                           "testExportPackage",
                                           "dsl" );

        String uuid2 = impl.createNewRule( "testExportPackageAsset2",
                                           "desc",
                                           "testExportPackageCat2",
                                           "testExportPackage",
                                           "dsl" );

        byte[] exportedPackage = repositoryPackageService.exportPackages( "testExportPackage" );

        assertNotNull( exportedPackage );

        File file = new File( "testExportPackage.xml" );

        FileOutputStream fos = new FileOutputStream( file );

        fos.write( exportedPackage );
        fos.close();

        file.delete();
    }

    @Test
    public void testArchivePackage() throws Exception {
        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        PackageConfigData[] pkgs = repositoryPackageService.listPackages();

        PackageConfigData[] arch = repositoryPackageService.listArchivedPackages();

        @SuppressWarnings("unused")
        String uuid = repositoryPackageService.createPackage( "testCreateArchivedPackage",
                                                              "this is a new package" );
        RepositoryAssetService repositoryAssetService = getRepositoryAssetService();
        PackageItem item = impl.getRulesRepository().loadPackage( "testCreateArchivedPackage" );
        TableDataResult td = repositoryAssetService.loadArchivedAssets( 0,
                                                                        1000 );

        item.archiveItem( true );

        TableDataResult td2 = repositoryAssetService.loadArchivedAssets( 0,
                                                                         1000 );
        assertEquals( td2.data.length,
                      td.data.length );

        PackageConfigData[] arch2 = repositoryPackageService.listArchivedPackages();
        assertEquals( arch2.length,
                      arch.length + 1 );

        assertEquals( pkgs.length,
                      repositoryPackageService.listPackages().length );

        item.archiveItem( false );
        arch2 = repositoryPackageService.listArchivedPackages();
        assertEquals( arch2.length,
                      arch.length );
    }

    @Test
    public void testCreatePackage() throws Exception {
        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        PackageConfigData[] pkgs = repositoryPackageService.listPackages();
        String uuid = repositoryPackageService.createPackage( "testCreatePackage",
                                                              "this is a new package" );
        assertNotNull( uuid );

        PackageItem item = impl.getRulesRepository().loadPackage( "testCreatePackage" );
        assertNotNull( item );
        assertEquals( "this is a new package",
                      item.getDescription() );

        assertEquals( pkgs.length + 1,
                      repositoryPackageService.listPackages().length );

        PackageConfigData conf = repositoryPackageService.loadPackageConfig( uuid );
        assertEquals( "this is a new package",
                      conf.description );
        assertNotNull( conf.lastModified );

        pkgs = repositoryPackageService.listPackages();

        repositoryPackageService.copyPackage( "testCreatePackage",
                                              "testCreatePackage_COPY" );

        assertEquals( pkgs.length + 1,
                      repositoryPackageService.listPackages().length );
        try {
            repositoryPackageService.copyPackage( "testCreatePackage",
                                                  "testCreatePackage_COPY" );
        } catch ( RulesRepositoryException e ) {
            assertNotNull( e.getMessage() );
        }
    }

    @Test
    public void testLoadPackageConfig() throws Exception {
        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        PackageItem it = impl.getRulesRepository().loadDefaultPackage();
        String uuid = it.getUUID();
        it.updateCoverage( "xyz" );
        it.updateExternalURI( "ext" );
        DroolsHeader.updateDroolsHeader( "header",
                                                  it );
        impl.getRulesRepository().save();

        PackageConfigData data = repositoryPackageService.loadPackageConfig( uuid );
        assertNotNull( data );

        assertEquals( RulesRepository.DEFAULT_PACKAGE,
                      data.name );
        assertEquals( "header",
                      data.header );
        assertEquals( "ext",
                      data.externalURI);

        assertNotNull( data.uuid);
        assertFalse( data.isSnapshot );

        assertNotNull( data.dateCreated );
        Date original = data.lastModified;

        Thread.sleep( 100 );

        repositoryPackageService.createPackageSnapshot( RulesRepository.DEFAULT_PACKAGE,
                                                        "TEST SNAP 2.0",
                                                        false,
                                                        "ya" );
        PackageItem loaded = impl.getRulesRepository().loadPackageSnapshot( RulesRepository.DEFAULT_PACKAGE,
                                                                            "TEST SNAP 2.0" );

        data = repositoryPackageService.loadPackageConfig( loaded.getUUID() );
        assertTrue( data.isSnapshot );
        assertEquals( "TEST SNAP 2.0",
                      data.snapshotName );
        assertFalse( original.equals( data.lastModified ) );
        assertEquals( "ya",
                      data.checkinComment);
    }

    @Test
    public void testArchiveAndUnarchivePackageAndHeader() throws Exception {
        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        String uuid = repositoryPackageService.createPackage( "testArchiveAndUnarchivePackageAndHeader",
                                                              "a desc" );
        PackageConfigData data = repositoryPackageService.loadPackageConfig( uuid );
        PackageItem it = impl.getRulesRepository().loadPackageByUUID( uuid );
        data.archived = true;

        AssetItem rule1 = it.addAsset( "rule_1",
                                       "" );
        rule1.updateFormat( AssetFormats.DRL );
        rule1.updateContent( "rule 'rule1' \n when \np : Person() \n then \np.setAge(42); \n end" );
        rule1.archiveItem( true );
        rule1.checkin( "" );
        impl.getRulesRepository().save();

        repositoryPackageService.savePackage( data );
        data = repositoryPackageService.loadPackageConfig( uuid );
        it = impl.getRulesRepository().loadPackage( data.name );
        assertTrue( data.archived );
        assertTrue( it.loadAsset( "drools" ).isArchived() );
        assertTrue( it.loadAsset( "rule_1" ).isArchived() );

        data.archived = false;

        repositoryPackageService.savePackage( data );
        data = repositoryPackageService.loadPackageConfig( uuid );
        it = impl.getRulesRepository().loadPackage( data.name );
        assertFalse( data.archived );
        assertFalse( it.loadAsset( "drools" ).isArchived() );
        assertTrue( it.loadAsset( "rule_1" ).isArchived() );

        data.archived = true;

        repositoryPackageService.savePackage( data );
        data = repositoryPackageService.loadPackageConfig( uuid );
        it = impl.getRulesRepository().loadPackage( data.name );
        assertTrue( data.archived );
        assertTrue( it.loadAsset( "drools" ).isArchived() );
        assertTrue( it.loadAsset( "rule_1" ).isArchived() );

    }

    @Test
    @Ignore("This test is broken. Suspect changes for new Workspaces and versionable packages could have broken.")
    public void testPackageConfSave() throws Exception {
        RepositoryService impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        String uuid = repositoryPackageService.createPackage( "testPackageConfSave",
                                                              "a desc" );
        PackageConfigData data = repositoryPackageService.loadPackageConfig( uuid );

        data.description = "new desc";
        data.header = "wa";
        data.externalURI = "new URI";

        ValidatedResponse res = repositoryPackageService.validatePackageConfiguration( data );
        assertNotNull( res );
        assertTrue( res.hasErrors );
        assertNotNull( res.errorMessage );

        data = repositoryPackageService.loadPackageConfig( uuid );
        assertEquals( "new desc",
                      data.description );
        assertEquals( "wa",
                      data.header );
        assertEquals( "new URI",
                      data.externalURI );

        data.header = "";
        res = repositoryPackageService.validatePackageConfiguration( data );
        if ( res.hasErrors ) {
            System.out.println( "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" );
            System.out.println( res.errorMessage );
            System.out.println( "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" );

        }

        assertFalse( res.hasErrors );
    }

    @Test
    public void testRemovePackage() throws Exception {
        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        int n = repositoryPackageService.listPackages().length;
        PackageItem p = impl.getRulesRepository().createPackage( "testRemovePackage",
                                                                 "" );
        assertNotNull( repositoryPackageService.loadPackageConfig( p.getUUID() ) );

        repositoryPackageService.removePackage( p.getUUID() );
        assertEquals( n,
                      repositoryPackageService.listPackages().length );
    }

    @Test
    @Ignore("Repository being imported does not exist. Need to create one!")
    public void testImportPackage() throws Exception {
        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        try {
            PackageItem item = impl.getRulesRepository().loadPackage( "testExportPackage" );
            fail();
            assertNull( item );
        } catch ( Exception e ) {
            // expected
        }

        //impl.createCategory( "/", "testExportPackageCat1", "desc" );
        //impl.createCategory( "/", "testExportPackageCat2", "desc" );

        File file = new File( "testExportPackage.xml" );

        FileInputStream fis = new FileInputStream( file );
        byte[] byteArray = new byte[fis.available()];
        fis.read( byteArray );

        repositoryPackageService.importPackages( byteArray,
                                                 true );

        PackageItem item = impl.getRulesRepository().loadPackage( "testExportPackage" );
        assertNotNull( item );
        assertEquals( "desc",
                      item.getDescription() );
        file.delete();
    }

    @Test
    public void testSnapshotDiffPagedResults() throws Exception {

        final int PAGE_SIZE = 2;

        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        RepositoryCategoryService repositoryCategoryService = getRepositoryCategoryService();

        // Lets make a package and put a rule into it
        repositoryCategoryService.createCategory( "/",
                                                  "snapshotDiffTestingCategory",
                                                  "snapshotDiffTestingCategoryDescription" );
        String packageUuid = repositoryPackageService.createPackage( "snapshotDiffTestingPackage",
                                                                     "snapshotDiffTestingPackageDescription" );
        assertNotNull( packageUuid );

        // Create some rules
        String archiveRuleUuid = impl.createNewRule( "testRuleArchived",
                                                     "testRuleArchivedDescription",
                                                     "snapshotDiffTestingCategory",
                                                     "snapshotDiffTestingPackage",
                                                     AssetFormats.DRL );
        String modifiedRuleUuid = impl.createNewRule( "testRuleModified",
                                                      "testRuleModifiedDescription",
                                                      "snapshotDiffTestingCategory",
                                                      "snapshotDiffTestingPackage",
                                                      AssetFormats.DRL );
        String deletedRuleUuid = impl.createNewRule( "testRuleDeleted",
                                                     "testRuleDeletedDescription",
                                                     "snapshotDiffTestingCategory",
                                                     "snapshotDiffTestingPackage",
                                                     AssetFormats.DRL );
        String restoredRuleUuid = impl.createNewRule( "testRuleRestored",
                                                      "testRuleRestoredDescription",
                                                      "snapshotDiffTestingCategory",
                                                      "snapshotDiffTestingPackage",
                                                      AssetFormats.DRL );
        RepositoryAssetService repositoryAssetService = getRepositoryAssetService();
        repositoryAssetService.archiveAsset( restoredRuleUuid );

        @SuppressWarnings("unused")
        String noChangesRuleUuid = impl.createNewRule( "testRuleNoChanges",
                                                       "testRuleNoChangesDescription",
                                                       "snapshotDiffTestingCategory",
                                                       "snapshotDiffTestingPackage",
                                                       AssetFormats.DRL );

        // Create a snapshot called FIRST for the package
        repositoryPackageService.createPackageSnapshot( "snapshotDiffTestingPackage",
                                                        "FIRST",
                                                        false,
                                                        "First snapshot" );
        assertEquals( 1,
                      repositoryPackageService.listSnapshots( "snapshotDiffTestingPackage" ).length );
        assertEquals( 4,
                      repositoryPackageService.listRulesInPackage( "snapshotDiffTestingPackage" ).length );

        // Change a rule...
        RuleAsset asset = repositoryAssetService.loadRuleAsset( modifiedRuleUuid );
        String uuid = repositoryAssetService.checkinVersion( asset );
        assertNotNull( uuid );

        //...delete one...
        repositoryAssetService.removeAsset( deletedRuleUuid );

        //...archive one...
        repositoryAssetService.archiveAsset( archiveRuleUuid );

        //...create a new one...
        @SuppressWarnings("unused")
        String addedRuleUuid = impl.createNewRule( "testRuleAdded",
                                                   "testRuleAddedDescription",
                                                   "snapshotDiffTestingCategory",
                                                   "snapshotDiffTestingPackage",
                                                   AssetFormats.DRL );

        //...and unarchive one
        repositoryAssetService.unArchiveAsset( restoredRuleUuid );

        // Create a snapshot called SECOND for the package
        repositoryPackageService.createPackageSnapshot( "snapshotDiffTestingPackage",
                                                        "SECOND",
                                                        false,
                                                        "Second snapshot" );
        assertEquals( 2,
                      repositoryPackageService.listSnapshots( "snapshotDiffTestingPackage" ).length );
        assertEquals( 4,
                      repositoryPackageService.listRulesInPackage( "snapshotDiffTestingPackage" ).length );

        // Compare the snapshots
        SnapshotComparisonPageRequest request = new SnapshotComparisonPageRequest( "snapshotDiffTestingPackage",
                                                                                   "FIRST",
                                                                                   "SECOND",
                                                                                   0,
                                                                                   PAGE_SIZE );
        SnapshotComparisonPageResponse response;
        response = repositoryPackageService.compareSnapshots( request );

        assertEquals( "FIRST",
                      response.getLeftSnapshotName() );
        assertEquals( "SECOND",
                      response.getRightSnapshotName() );

        assertNotNull( response );
        assertNotNull( response.getPageRowList() );
        assertTrue( response.getStartRowIndex() == 0 );
        assertTrue( response.getPageRowList().size() == PAGE_SIZE );
        assertFalse( response.isLastPage() );

        request.setStartRowIndex( PAGE_SIZE );
        response = repositoryPackageService.compareSnapshots( request );

        assertNotNull( response );
        assertNotNull( response.getPageRowList() );
        assertTrue( response.getStartRowIndex() == PAGE_SIZE );
        assertTrue( response.getPageRowList().size() == PAGE_SIZE );
        assertFalse( response.isLastPage() );

        request.setStartRowIndex( PAGE_SIZE * 2 );
        response = repositoryPackageService.compareSnapshots( request );

        assertNotNull( response );
        assertNotNull( response.getPageRowList() );
        assertTrue( response.getStartRowIndex() == PAGE_SIZE * 2 );
        assertTrue( response.getPageRowList().size() == 1 );
        assertTrue( response.isLastPage() );
    }

    @Test
    public void testSnapshotDiffFullResults() throws Exception {

        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        RepositoryCategoryService repositoryCategoryService = getRepositoryCategoryService();

        // Lets make a package and put a rule into it
        repositoryCategoryService.createCategory( "/",
                                                  "snapshotDiffTestingCategory",
                                                  "snapshotDiffTestingCategoryDescription" );
        String packageUuid = repositoryPackageService.createPackage( "snapshotDiffTestingPackage",
                                                                     "snapshotDiffTestingPackageDescription" );
        assertNotNull( packageUuid );

        // Create some rules
        String archiveRuleUuid = impl.createNewRule( "testRuleArchived",
                                                     "testRuleArchivedDescription",
                                                     "snapshotDiffTestingCategory",
                                                     "snapshotDiffTestingPackage",
                                                     AssetFormats.DRL );
        String modifiedRuleUuid = impl.createNewRule( "testRuleModified",
                                                      "testRuleModifiedDescription",
                                                      "snapshotDiffTestingCategory",
                                                      "snapshotDiffTestingPackage",
                                                      AssetFormats.DRL );
        String deletedRuleUuid = impl.createNewRule( "testRuleDeleted",
                                                     "testRuleDeletedDescription",
                                                     "snapshotDiffTestingCategory",
                                                     "snapshotDiffTestingPackage",
                                                     AssetFormats.DRL );
        String restoredRuleUuid = impl.createNewRule( "testRuleRestored",
                                                      "testRuleRestoredDescription",
                                                      "snapshotDiffTestingCategory",
                                                      "snapshotDiffTestingPackage",
                                                      AssetFormats.DRL );
        RepositoryAssetService repositoryAssetService = getRepositoryAssetService();
        repositoryAssetService.archiveAsset( restoredRuleUuid );

        @SuppressWarnings("unused")
        String noChangesRuleUuid = impl.createNewRule( "testRuleNoChanges",
                                                       "testRuleNoChangesDescription",
                                                       "snapshotDiffTestingCategory",
                                                       "snapshotDiffTestingPackage",
                                                       AssetFormats.DRL );

        // Create a snapshot called FIRST for the package
        repositoryPackageService.createPackageSnapshot( "snapshotDiffTestingPackage",
                                                        "FIRST",
                                                        false,
                                                        "First snapshot" );
        assertEquals( 1,
                      repositoryPackageService.listSnapshots( "snapshotDiffTestingPackage" ).length );
        assertEquals( 4,
                      repositoryPackageService.listRulesInPackage( "snapshotDiffTestingPackage" ).length );

        // Change a rule...
        RuleAsset asset = repositoryAssetService.loadRuleAsset( modifiedRuleUuid );
        String uuid = repositoryAssetService.checkinVersion( asset );
        assertNotNull( uuid );

        //...delete one...
        repositoryAssetService.removeAsset( deletedRuleUuid );

        //...archive one...
        repositoryAssetService.archiveAsset( archiveRuleUuid );

        //...create a new one...
        @SuppressWarnings("unused")
        String addedRuleUuid = impl.createNewRule( "testRuleAdded",
                                                   "testRuleAddedDescription",
                                                   "snapshotDiffTestingCategory",
                                                   "snapshotDiffTestingPackage",
                                                   AssetFormats.DRL );

        //...and unarchive one
        repositoryAssetService.unArchiveAsset( restoredRuleUuid );

        // Create a snapshot called SECOND for the package
        repositoryPackageService.createPackageSnapshot( "snapshotDiffTestingPackage",
                                                        "SECOND",
                                                        false,
                                                        "Second snapshot" );
        assertEquals( 2,
                      repositoryPackageService.listSnapshots( "snapshotDiffTestingPackage" ).length );
        assertEquals( 4,
                      repositoryPackageService.listRulesInPackage( "snapshotDiffTestingPackage" ).length );

        // Compare the snapshots
        SnapshotComparisonPageRequest request = new SnapshotComparisonPageRequest( "snapshotDiffTestingPackage",
                                                                                   "FIRST",
                                                                                   "SECOND",
                                                                                   0,
                                                                                   null );
        SnapshotComparisonPageResponse response;
        response = repositoryPackageService.compareSnapshots( request );

        assertEquals( "FIRST",
                      response.getLeftSnapshotName() );
        assertEquals( "SECOND",
                      response.getRightSnapshotName() );

        assertNotNull( response );
        assertNotNull( response.getPageRowList() );
        assertTrue( response.getStartRowIndex() == 0 );
        assertTrue( response.getPageRowList().size() == 5 );
        assertTrue( response.isLastPage() );

        for ( SnapshotComparisonPageRow row : response.getPageRowList() ) {
            SnapshotDiff diff = row.getDiff();
            if ( diff.name.equals( "testRuleArchived" ) ) {
                assertEquals( SnapshotDiff.TYPE_ARCHIVED,
                              diff.diffType );
                assertNotNull( diff.leftUuid );
                assertNotNull( diff.rightUuid );
            } else if ( diff.name.equals( "testRuleModified" ) ) {
                assertEquals( SnapshotDiff.TYPE_UPDATED,
                              diff.diffType );
                assertNotNull( diff.leftUuid );
                assertNotNull( diff.rightUuid );
            } else if ( diff.name.equals( "testRuleAdded" ) ) {
                assertEquals( SnapshotDiff.TYPE_ADDED,
                              diff.diffType );
                assertNull( diff.leftUuid );
                assertNotNull( diff.rightUuid );
            } else if ( diff.name.equals( "testRuleDeleted" ) ) {
                assertEquals( SnapshotDiff.TYPE_DELETED,
                              diff.diffType );
                assertNotNull( diff.leftUuid );
                assertNull( diff.rightUuid );
            } else if ( diff.name.equals( "testRuleRestored" ) ) {
                assertEquals( SnapshotDiff.TYPE_RESTORED,
                              diff.diffType );
                assertNotNull( diff.leftUuid );
                assertNotNull( diff.rightUuid );
            } else {
                fail( "Diff not expected." );
            }
        }
    }

    /**
     * This will test creating a package, check it compiles, and can exectute
     * rules, then take a snapshot, and check that it reports errors.
     */

    @Test
    public void testBinaryPackageCompileAndExecute() throws Exception {
        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        RulesRepository repo = impl.getRulesRepository();

        // create our package
        PackageItem pkg = repo.createPackage( "testBinaryPackageCompile",
                                              "" );
        DroolsHeader.updateDroolsHeader( "global java.util.List ls \n import org.drools.Person",
                                                  pkg );
        AssetItem rule1 = pkg.addAsset( "rule_1",
                                        "" );
        rule1.updateFormat( AssetFormats.DRL );
        rule1.updateContent( "rule 'rule1' \n when \np : Person() \n then \np.setAge(42); \n end" );
        rule1.checkin( "" );
        repo.save();

        BuilderResult result = repositoryPackageService.buildPackage( pkg.getUUID(),
                                                                      true );
        assertNull( result );

        pkg = repo.loadPackage( "testBinaryPackageCompile" );
        byte[] binPackage = pkg.getCompiledPackageBytes();

        assertNotNull( binPackage );

        Package[] binPkgs = (Package[]) DroolsStreamUtils.streamIn( binPackage );
        assertNotNull( binPkgs );
        assertEquals( 1,
                      binPkgs.length );
         
        Package binPkg = binPkgs[0];
        assertNotNull( binPkg );
        assertTrue( binPkg.isValid() );

        Person p = new Person();

        BinaryRuleBaseLoader loader = new BinaryRuleBaseLoader();
        loader.addPackage( new ByteArrayInputStream( binPackage ) );
        RuleBase rb = loader.getRuleBase();

        StatelessSession sess = rb.newStatelessSession();
        sess.setGlobal( "ls",
                        new ArrayList<String>() );
        sess.execute( p );

        assertEquals( 42,
                      p.getAge() );

        repositoryPackageService.createPackageSnapshot( "testBinaryPackageCompile",
                                                        "SNAP1",
                                                        false,
                                                        "" );

        rule1.updateContent( "rule 'rule1' \n when p:PersonX() \n then System.err.println(42); \n end" );
        rule1.checkin( "" );

        result = repositoryPackageService.buildPackage( pkg.getUUID(),
                                                        true );
        assertNotNull( result );
        assertEquals( 1,
                      result.getLines().size() );
        assertEquals( rule1.getName(),
                      result.getLines().get( 0 ).getAssetName() );
        assertEquals( AssetFormats.DRL,
                      result.getLines().get( 0 ).getAssetFormat() );
        assertNotNull( result.getLines().get( 0 ).getMessage() );
        assertEquals( rule1.getUUID(),
                      result.getLines().get( 0 ).getUuid() );

        pkg = repo.loadPackageSnapshot( "testBinaryPackageCompile",
                                        "SNAP1" );
        result = repositoryPackageService.buildPackage( pkg.getUUID(),
                                                        true );
        assertNull( result );

    }

    /**
     * This will test creating a package with a BRL rule, check it compiles, and
     * can exectute rules, then take a snapshot, and check that it reports
     * errors.
     */

    @Test
    public void testBinaryPackageCompileAndExecuteWithBRXML() throws Exception {
        ServiceImplementation impl = getServiceImplementation();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        RulesRepository repo = impl.getRulesRepository();

        // create our package
        PackageItem pkg = repo.createPackage( "testBinaryPackageCompileBRL",
                                              "" );
        DroolsHeader.updateDroolsHeader( "import org.drools.Person",
                                                  pkg );
        AssetItem rule2 = pkg.addAsset( "rule2",
                                        "" );
        rule2.updateFormat( AssetFormats.BUSINESS_RULE );

        RuleModel model = new RuleModel();
        model.name = "rule2";
        FactPattern pattern = new FactPattern( "Person" );

        SingleFieldConstraint con = new SingleFieldConstraint();
        con.setConstraintValueType( BaseSingleFieldConstraint.TYPE_PREDICATE );
        con.setValue( "name soundslike 'foobar'" );
        pattern.addConstraint( con );

        pattern.setBoundName( "p" );
        ActionSetField action = new ActionSetField( "p" );
        ActionFieldValue value = new ActionFieldValue( "age",
                                                       "42",
                                                       SuggestionCompletionEngine.TYPE_NUMERIC );
        action.addFieldValue( value );

        model.addLhsItem( pattern );
        model.addRhsItem( action );

        rule2.updateContent( BRXMLPersistence.getInstance().marshal( model ) );
        rule2.checkin( "" );
        repo.save();

        BuilderResult result = repositoryPackageService.buildPackage( pkg.getUUID(),
                                                                      true );
        if ( result != null ) {
            for ( int i = 0; i < result.getLines().size(); i++ ) {
                System.err.println( result.getLines().get( i ).getMessage() );
            }
        }
        assertNull( result );

        pkg = repo.loadPackage( "testBinaryPackageCompileBRL" );
        byte[] binPackage = pkg.getCompiledPackageBytes();

        // Here is where we write it out is needed... set to true if needed for
        // the binary test below "testLoadAndExecBinary"
        boolean saveBinPackage = false;
        if ( saveBinPackage ) {
            FileOutputStream out = new FileOutputStream( "RepoBinPackage.pkg" );
            out.write( binPackage );
            out.flush();
            out.close();
        }

        assertNotNull( binPackage );

        Package[] binPkgs = (Package[]) DroolsStreamUtils.streamIn( binPackage );
        assertNotNull( binPkgs );
        assertEquals( 1,
                      binPkgs.length );
         
        Package binPkg = binPkgs[0];
        assertNotNull( binPkg );
        assertTrue( binPkg.isValid() );

        // and this shows off the "soundex" thing...
        Person p = new Person( "fubar" );

        BinaryRuleBaseLoader loader = new BinaryRuleBaseLoader();
        loader.addPackage( new ByteArrayInputStream( binPackage ) );
        RuleBase rb = loader.getRuleBase();

        StatelessSession sess = rb.newStatelessSession();
        sess.execute( p );
        assertEquals( 42,
                      p.getAge() );

        repositoryPackageService.createPackageSnapshot( "testBinaryPackageCompileBRL",
                                                        "SNAP1",
                                                        false,
                                                        "" );

        pattern.setFactType( "PersonX" );
        rule2.updateContent( BRXMLPersistence.getInstance().marshal( model ) );
        rule2.checkin( "" );

        result = repositoryPackageService.buildPackage( pkg.getUUID(),
                                                        true );
        assertNotNull( result );
        assertTrue( result.getLines().size() > 0 );
        // assertEquals(2, results.length);
        assertEquals( rule2.getName(),
                      result.getLines().get( 0 ).getAssetName() );
        assertEquals( AssetFormats.BUSINESS_RULE,
                      result.getLines().get( 0 ).getAssetFormat() );
        assertNotNull( result.getLines().get( 0 ).getMessage() );
        assertEquals( rule2.getUUID(),
                      result.getLines().get( 0 ).getUuid() );

        pkg = repo.loadPackageSnapshot( "testBinaryPackageCompileBRL",
                                        "SNAP1" );
        result = repositoryPackageService.buildPackage( pkg.getUUID(),
                                                        true );
        assertNull( result );

        // check that the rule name in the model is being set
        AssetItem asset2 = pkg.addAsset( "testSetRuleName",
                                         "" );
        asset2.updateFormat( AssetFormats.BUSINESS_RULE );
        asset2.checkin( "" );

        RuleModel model2 = new RuleModel();
        assertNull( model2.name );
        RepositoryAssetService repositoryAssetService = getRepositoryAssetService();
        RuleAsset asset = repositoryAssetService.loadRuleAsset( asset2.getUUID() );
        asset.content = (PortableObject) model2;

        repositoryAssetService.checkinVersion( asset );

        asset = repositoryAssetService.loadRuleAsset( asset2.getUUID() );

        model2 = (RuleModel) asset.content;
        assertNotNull( model2 );
        assertNotNull( model2.name );
        assertEquals( asset2.getName(),
                      model2.name );

    }

    @Test
    public void testPackageSource() throws Exception {
        ServiceImplementation impl = getServiceImplementation();
        RulesRepository repo = impl.getRulesRepository();
        RepositoryPackageService repositoryPackageService = getRepositoryPackageService();
        // create our package
        PackageItem pkg = repo.createPackage( "testPackageSource",
                                              "" );
        DroolsHeader.updateDroolsHeader( "import org.goo.Ber",
                                                  pkg );
        AssetItem rule1 = pkg.addAsset( "rule_1",
                                        "" );
        rule1.updateFormat( AssetFormats.DRL );
        rule1.updateContent( "rule 'rule1' \n when p:Person() \n then p.setAge(42); \n end" );
        rule1.checkin( "" );
        repo.save();

        AssetItem func = pkg.addAsset( "funky",
                                       "" );
        func.updateFormat( AssetFormats.FUNCTION );
        func.updateContent( "this is a func" );
        func.checkin( "" );

        String drl = repositoryPackageService.buildPackageSource( pkg.getUUID() );
        assertNotNull( drl );

        assertTrue( drl.indexOf( "import org.goo.Ber" ) > -1 );
        assertTrue( drl.indexOf( "package testPackageSource" ) > -1 );
        assertTrue( drl.indexOf( "rule 'rule1'" ) > -1 );
        assertTrue( drl.indexOf( "this is a func" ) > -1 );
        assertTrue( drl.indexOf( "this is a func" ) < drl.indexOf( "rule 'rule1'" ) );
        assertTrue( drl.indexOf( "package testPackageSource" ) < drl.indexOf( "this is a func" ) );
        assertTrue( drl.indexOf( "package testPackageSource" ) < drl.indexOf( "import org.goo.Ber" ) );

        AssetItem dsl = pkg.addAsset( "MyDSL",
                                      "" );
        dsl.updateFormat( AssetFormats.DSL );
        dsl.updateContent( "[when]This is foo=bar()\n[then]do something=yeahMan();" );
        dsl.checkin( "" );

        AssetItem asset = pkg.addAsset( "MyDSLRule",
                                        "" );
        asset.updateFormat( AssetFormats.DSL_TEMPLATE_RULE );
        asset.updateContent( "when \n This is foo \n then \n do something" );
        asset.checkin( "" );

        drl = repositoryPackageService.buildPackageSource( pkg.getUUID() );
        assertNotNull( drl );

        assertTrue( drl.indexOf( "import org.goo.Ber" ) > -1 );
        assertTrue( drl.indexOf( "This is foo" ) == -1 );
        assertTrue( drl.indexOf( "do something" ) == -1 );
        assertTrue( drl.indexOf( "bar()" ) > 0 );
        assertTrue( drl.indexOf( "yeahMan();" ) > 0 );

    }
}
