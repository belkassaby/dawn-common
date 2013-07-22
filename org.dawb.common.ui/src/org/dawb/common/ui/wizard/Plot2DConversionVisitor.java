package org.dawb.common.ui.wizard;

import java.io.File;
import java.util.Collection;

import org.dawb.common.services.conversion.IConversionContext;
import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlotType;
import org.dawnsci.plotting.api.trace.IImageTrace;
import org.dawnsci.plotting.api.trace.ITrace;

import uk.ac.diamond.scisoft.analysis.dataset.ADataset;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.io.JavaImageSaver;

/**
 * TODO FIXME This is not a UI class. Suggest move to data/algorithm plugin like org.dawnsci.persistence perhaps.
 *
 */
public class Plot2DConversionVisitor extends AbstractPlotConversionVisitor {

	public Plot2DConversionVisitor(IPlottingSystem system) {
		super(system);
		if (system.getPlotType() != PlotType.IMAGE) {
			throw new IllegalArgumentException("Not a 2D plotting system");
		}
	}

	@Override
	public void visit(IConversionContext context, IDataset slice)
			throws Exception {
		
		if (context.getMonitor()!=null && context.getMonitor().isCancelled()) {
			throw new Exception(getClass().getSimpleName()+" is cancelled");
		}
		
		Collection<ITrace> traces = system.getTraces(IImageTrace.class);
		
		if (traces.size() != 1) throw new IllegalArgumentException("Only expect one image in a 2D plot");
		
		for (ITrace trace : traces) {
			if (trace instanceof IImageTrace) {
				
				IDataset data = trace.getData();
				
				int bits = 33;
				int dbits = AbstractDataset.getDType(data);
				
				switch (dbits) {
				case ADataset.INT8:
					bits = 8;
					break;
				case ADataset.INT16:
					bits = 16;
					break;
				case ADataset.INT32:
					bits = 32;
					break;
				}
				
				final File outFile = new File(context.getOutputPath());
				if (!outFile.getParentFile().exists()) outFile.getParentFile().mkdirs();
				final JavaImageSaver saver = new JavaImageSaver(outFile.getAbsolutePath(), getExtension(),bits, true);
				final DataHolder     dh    = new DataHolder();
				dh.addDataset(data.getName(), data);
				saver.saveFile(dh);
				if (context.getMonitor()!=null) context.getMonitor().worked(1);
				
			} else {
				throw new IllegalArgumentException("Non-image trace in a 2D plot");
			}
		}
	}


	@Override
	public String getExtension() {
		return "tiff";
	}
}
