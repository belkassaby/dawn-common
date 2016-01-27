/*-
 * Copyright 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.dawnsci.nexus;

import static org.dawnsci.nexus.NexusAssert.assertNexusTreesEqual;

import org.eclipse.dawnsci.analysis.api.tree.TreeFile;
import org.eclipse.dawnsci.analysis.tree.impl.TreeFileImpl;
import org.eclipse.dawnsci.nexus.NXroot;
import org.eclipse.dawnsci.nexus.NexusNodeFactory;
import org.eclipse.dawnsci.nexus.test.util.NexusTestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.TestUtils;

public abstract class AbstractNexusFileTestBase {

	protected NexusNodeFactory nexusNodeFactory;

	private static String testScratchDirectoryName;

	private String filePath;

	@Before
	public void setUp() throws Exception {
		testScratchDirectoryName = TestUtils.generateDirectorynameFromClassname(getClass().getCanonicalName());
		TestUtils.makeScratchDirectory(testScratchDirectoryName);
		nexusNodeFactory = new NexusNodeFactory();
		filePath = testScratchDirectoryName + getFilename();
	}

	@After
	public void tearDown() {
		nexusNodeFactory = null;
	}

	protected abstract String getFilename();

	protected abstract NXroot createNXroot();

	private TreeFile createNexusTree() {
		final TreeFileImpl treeFile = nexusNodeFactory.createTreeFile(filePath);
		final NXroot root = createNXroot();
		treeFile.setGroupNode(root);

		return treeFile;
	}
	
	@Test
	public void testNexusFile() throws Exception {
		TreeFile actualNexusTree = createNexusTree();
		NexusTestUtils.saveNexusFile(actualNexusTree);

		TreeFile expectedTree = NexusTestUtils.loadNexusFile(filePath, true);
		assertNexusTreesEqual(expectedTree, actualNexusTree);
	}

}
