package org.dawnsci.persistence.test.operations;

import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.january.IMonitor;
import org.eclipse.january.MetadataException;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.Random;
import org.eclipse.january.metadata.AxesMetadata;
import org.eclipse.january.metadata.MetadataFactory;

public class JunkTestOperation extends AbstractOperation<JunkTestOperationModel,OperationData> {

	@Override
	public String getId() {
		return "org.dawnsci.persistence.test.operations.JunkTestOperation";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ONE;
	}


	@Override
	public OperationRank getOutputRank() {
		return OperationRank.ONE;
	}
	
	@Override
	public String getName(){
		return "Junk1Dto1DOperation";
	}
	
	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		
		int x = model.getxDim();

		IDataset out = Random.rand(new int[] {x});
		out.setName("Junk1Dout");
		IDataset ax1 = DatasetFactory.createRange(0, x,1, Dataset.INT16);
		ax1.setShape(new int[]{20,1});
		ax1.setName("Junk1Dax");
		
		AxesMetadata am;
		try {
			am = MetadataFactory.createMetadata(AxesMetadata.class, 1);
		} catch (MetadataException e) {
			throw new OperationException(this, e);
		}
		am.addAxis(0, ax1);
		
		out.setMetadata(am);
		
		return new OperationData(out);
	}

}
