/*-
 * Copyright (c) 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.dawnsci.conversion.converters;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dawb.common.services.conversion.IConversionContext;
import org.dawb.common.util.list.SortNatural;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.dataset.impl.LazyDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.io.ImageStackLoader;
import uk.ac.diamond.scisoft.analysis.io.JavaImageSaver;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;

/**
 * Aligns a stack or directory of images
 * 
 * @author Baha El Kassaby
 * 
 */
public class AlignImagesConverter extends AbstractImageConversion {

	private static final Logger logger = LoggerFactory.getLogger(AlignImagesConverter.class);
	private List<IDataset> imageStack = new ArrayList<IDataset>();
	private List<IDataset> alignedImages = new ArrayList<IDataset>();

	public AlignImagesConverter() {
		super(null);
	}
	
	public AlignImagesConverter(IConversionContext context) throws Exception {
		super(context);
		final File dir = new File(context.getOutputPath());
		dir.mkdirs();
		// We put the many files in one ILazyDataset and set that in the context
		// as an override.
		ILazyDataset set = getLazyDataset();
		context.setLazyDataset(set);
		context.addSliceDimension(0, "all");
	}
	
	@Override
	protected void convert(IDataset slice) throws Exception {

		if (context.getMonitor() != null && context.getMonitor().isCancelled()) {
			throw new Exception(getClass().getSimpleName() + " is cancelled");
		}
		ILazyDataset lazy = context.getLazyDataset();
		imageStack.add(slice);
		int stackSize = lazy.getShape()[0];
		if (imageStack.size() == stackSize) {
			String outputPath = context.getOutputPath();
			ConversionAlignBean conversionBean = (ConversionAlignBean)context.getUserObject();
			// TODO save aligned
			alignedImages = conversionBean.getAligned();
			
			final File outputFile = new File(outputPath);

			if (!outputFile.getParentFile().exists())
				outputFile.getParentFile().mkdirs();

			// JavaImageSaver likes 33 but users don't
			int bits = getBits();
			if (bits == 32 && getExtension().toLowerCase().startsWith("tif"))
				bits = 33;

			final JavaImageSaver saver = new JavaImageSaver(
					outputFile.getAbsolutePath(), getExtension(), bits, true);
			final DataHolder dh = new DataHolder();
			dh.addDataset(alignedImages.get(0).getName(), alignedImages.get(0));
			dh.setFilePath(outputFile.getAbsolutePath());
			saver.saveFile(dh);
		}
		if (context.getMonitor() != null)
			context.getMonitor().worked(1);
	}

	private ILazyDataset getLazyDataset() throws Exception {
		List<String> regexs = context.getFilePaths();
		final List<String> paths = new ArrayList<String>(Math.max(
				regexs.size(), 10));
		ILazyDataset lazyDataset = null;
		// if dat file: try to parse it and populate the list of all images paths 
		if (regexs.size() == 1 && regexs.get(0).endsWith(".dat")) {
			IDataHolder holder = LoaderFactory.getData(regexs.get(0));
			String[] names = holder.getNames();
			for (String name : names) {
				lazyDataset = holder.getLazyDataset(name);
				if (lazyDataset != null && lazyDataset.getRank() == 3) 
					return lazyDataset;
			}
		}
		for (String regex : regexs) {
			final List<File> files = expand(regex);
			for (File file : files) {
				try {
					ILazyDataset data = LoaderFactory.getData(
							file.getAbsolutePath(), context.getMonitor())
							.getLazyDataset(0);
					if (data.getRank() == 2)
						paths.add(file.getAbsolutePath());
				} catch (Exception ignored) {
					logger.debug("Exception ignored:"+ignored.getMessage());
					continue;
				}
			}
		}
		Collections.sort(paths, new SortNatural<String>(true));
		ImageStackLoader loader = new ImageStackLoader(paths,
				context.getMonitor());
		lazyDataset = new LazyDataset("Folder Stack",
				loader.getDtype(), loader.getShape(), loader);
		return lazyDataset;
	}

	@Override
	public void close(IConversionContext context) {

	}

	protected String getExtension() {
		if (context.getUserObject() == null)
			return "tif";
		return ((ConversionInfoBean) context.getUserObject()).getExtension();
	}

	private int getBits() {
		if (context.getUserObject() == null)
			return 33;
		return ((ConversionInfoBean) context.getUserObject()).getBits();
	}

	/**
	 * To be used as the user object to convey data about the align conversion.
	 *
	 */
	public static class ConversionAlignBean extends ConversionInfoBean {
		private List<IDataset> aligned;

		public void setAligned(List<IDataset> aligned) {
			this.aligned = aligned;
		}
		public List<IDataset> getAligned() {
			return aligned;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result
					+ ((aligned == null) ? 0 : aligned.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (getClass() != obj.getClass())
				return false;
			ConversionAlignBean other = (ConversionAlignBean) obj;
			if (aligned == null) {
				if (other.aligned != null)
					return false;
			} else if (!aligned.equals(other.aligned))
				return false;
			return true;
		}
		
	}
}