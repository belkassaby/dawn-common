package org.dawnsci.conversion.converters;


import gda.analysis.io.ScanFileHolderException;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.UnitFormat;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;

import ncsa.hdf.object.Dataset;

import org.apache.commons.lang.ArrayUtils;
import org.cansas.cansas1d.FloatUnitType;
import org.cansas.cansas1d.IdataType;
import org.cansas.cansas1d.ObjectFactory;
import org.cansas.cansas1d.SAScollimationType;
import org.cansas.cansas1d.SASdataType;
import org.cansas.cansas1d.SASdetectorType;
import org.cansas.cansas1d.SASentryType;
import org.cansas.cansas1d.SASentryType.Run;
import org.cansas.cansas1d.SASinstrumentType;
import org.cansas.cansas1d.SASrootType;
import org.cansas.cansas1d.SASsampleType;
import org.cansas.cansas1d.SASsourceType;
import org.cansas.cansas1d.SAStransmissionSpectrumType;
import org.dawb.common.services.conversion.IConversionContext;
import org.dawb.hdf5.HierarchicalDataFactory;
import org.dawb.hdf5.IHierarchicalDataFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IErrorDataset;
import uk.ac.diamond.scisoft.analysis.dataset.ILazyDataset;
import uk.ac.diamond.scisoft.analysis.dataset.PositionIterator;
import uk.ac.diamond.scisoft.analysis.dataset.Slice;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Node;
import uk.ac.diamond.scisoft.analysis.io.ASCIIDataWithHeadingSaver;
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.analysis.monitor.IMonitor;

public class CustomNCDConverter extends AbstractConversion  {

	private static final Logger logger = LoggerFactory.getLogger(CustomNCDConverter.class);
	private static final String DEFAULT_AXIS_NAME = "x";
	private static final String DEFAULT_COLUMN_NAME = "Column";
	private static final String DEFAULT_ERRORS_COLUMN_NAME = "Error";
	private static final String DEFAULT_TITLE_NODE = "/entry1/title";
	private static final String DEFAULT_SCAN_COMMAND_NODE = "/entry1/scan_command";
	private static final String CANSAS_JAXB_CONTEXT = "org.cansas.cansas1d";
	
	public static enum SAS_FORMAT { ASCII, ATSAS, CANSAS };

	public CustomNCDConverter(IConversionContext context) {
		super(context);
		final File dir = new File(context.getOutputPath());
		dir.mkdirs();
	}

	@Override
	protected void convert(IDataset slice) {
		//we do our convert elsewhere
	}
	
	@Override
	protected void iterate(final ILazyDataset         lz, 
            final String               nameFrag,
            final IConversionContext   context) throws Exception {
		
		Object obj = context.getUserObject();
		SAS_FORMAT exportFormat;
		if (obj instanceof SAS_FORMAT) {
			exportFormat = (SAS_FORMAT) obj;
		} else {
			exportFormat = SAS_FORMAT.ASCII;
		}
		
		if (exportFormat.equals(SAS_FORMAT.CANSAS)) {
			exportCanSAS(lz, nameFrag, context);
			return;
		}
		
		String selFilePath = context.getSelectedConversionFile().getAbsolutePath();
		IHierarchicalDataFile hdf5Reader = HierarchicalDataFactory.getReader(selFilePath);
		
		try {
			//get the x axis if required
			IErrorDataset axis = null;
			if (context.getAxisDatasetName() != null) {
				axis = (IErrorDataset)getAxis(context.getAxisDatasetName(), context.getSelectedConversionFile());
				// ATSAS ASCII format doesn't support axis errors
				if (axis.hasErrors() && exportFormat.equals(SAS_FORMAT.ATSAS)) {
					axis.clearError();
				}
			}
			
			//Set up position iterator (final 2 dimensions saved in a single file
			int[] stop = lz.getShape();
			boolean hasErrors = (lz.getLazyErrors() != null ? true : false);
			int iterDim;
			int[] cutAxes;
			if (stop.length == 1 || exportFormat.equals(SAS_FORMAT.ATSAS)) {
				iterDim = lz.getRank() - 1;
				cutAxes = new int[] {lz.getRank() - 1};
			} else {
				iterDim = lz.getRank() - 2;
				cutAxes = new int[] {lz.getRank() - 2, lz.getRank() - 1};
			}
			
			PositionIterator iterator = new PositionIterator(stop, cutAxes);
			
			for (int i = 0 ; i < iterDim ; i++) {
				stop[i] = 0;
			}
			
			int[] step = new int[stop.length];
			for (int i = 0 ; i < step.length; i++) {
				step[i] = 1;
			}
			
			//Make file header and column names
			final String separator = System.getProperty("line.separator");
			StringBuilder sb = new StringBuilder();
			sb.append("# Diamond Light Source Ltd.");
			sb.append(separator);
			sb.append("# Non Crystalline Diffraction Results Export File");
			sb.append(separator);
			sb.append("# Data extracted from file: " + selFilePath);
			sb.append(separator);
			sb.append("# Dataset name: " + nameFrag);
			
			try {
				Dataset titleData = (Dataset) hdf5Reader.getData(DEFAULT_TITLE_NODE);
				String[] str = (String[]) titleData.getData();
				if (str.length > 0) {
					String title = str[0];
					sb.append(separator);
					sb.append("# Title: " + title);
				}
			} catch (Exception e) {
				logger.info("Default title node {} was not found", DEFAULT_TITLE_NODE);
			}
			try {
				Dataset scanCommandData = (Dataset)hdf5Reader.getData(DEFAULT_SCAN_COMMAND_NODE);
				String[] str = (String[])scanCommandData.getData();
				if (str.length > 0) {
					String scanCommand = str[0];
					sb.append(separator);
					sb.append("# Scan command: " + scanCommand);
				}
			} catch (Exception e) {
				logger.info("Default scan command node {} was not found", DEFAULT_SCAN_COMMAND_NODE);
			}
			
			List<String> headings = new ArrayList<String>();
			String stringFormat = "%-12s";
			
			if (axis != null) {
				String axisUnit = getAxisUnit(context.getAxisDatasetName(), context.getSelectedConversionFile());
				String axisName = String.format(stringFormat, String.format("%s(%s)", axis.getName(), axisUnit));
				headings.add(" ".concat(axisName));
				if (axis.hasErrors()) {
					headings.add(String.format(stringFormat, String.format("%s(%s)", axis.getName().concat("_errors"), axisUnit)));
				}
			}
			
			if (stop.length == 1 || exportFormat.equals(SAS_FORMAT.ATSAS)) {
				headings.add(String.format(stringFormat,DEFAULT_COLUMN_NAME));
				if (hasErrors) {
					headings.add(String.format(stringFormat,DEFAULT_ERRORS_COLUMN_NAME));
				}
			} else {
				for (int i = 0; i< stop[iterDim]; i++) {
					headings.add(String.format(stringFormat,DEFAULT_COLUMN_NAME + "_" + i));
				}
				if (hasErrors) {
					for (int i = 0; i< stop[iterDim]; i++) {
						headings.add(String.format(stringFormat,DEFAULT_ERRORS_COLUMN_NAME + "_" + i));
					}
				}
			}
			
			//Iterate over lazy dataset and save
			while (iterator.hasNext()) {
				
				int[] start = iterator.getPos();
				
				for (int j = 0 ; j < iterDim ; j++) {
					stop[j] = start[j]+1;
				}
				
				Slice[] slices = Slice.convertToSlice(start, stop, step);
				IDataset data = lz.getSlice(slices);
				data = (IDataset)data.squeeze();
				
				AbstractDataset errors = null;
				if (hasErrors) {
					errors = DatasetUtils.cast((AbstractDataset) ((IErrorDataset) data).getError(),
							((AbstractDataset)data).getDtype());
				}
				
				String nameSuffix = "";
				
				if (!(Arrays.equals(lz.getShape(), data.getShape()))) {
					nameSuffix = nameStringFromSliceArray(iterDim, slices);
				}
				data.setName(nameFrag + nameStringFromSliceArray(iterDim, slices));
				String pathToFolder = context.getOutputPath();
				String fileName = buildFileName(context.getSelectedConversionFile().getAbsolutePath(),nameFrag);
				String fullName = pathToFolder + File.separator + fileName + nameSuffix +".dat";
				
				
				//Check data suitable then concatenate axis with data
				if (data.getRank() == 1) {
					data.setShape(1,data.getShape()[0]);
					if (hasErrors) {
						errors.setShape(1,errors.getShape()[0]);
					}
				}
				
				exportASCII(axis, data, errors, fullName, sb.toString(), headings);
					
				if (context.getMonitor() != null) {
					IMonitor mon = context.getMonitor();
					if (mon.isCancelled()) {
						return;
					}
					context.getMonitor().subTask(fileName + nameSuffix);
				}
			}
			
			if (context.getMonitor() != null) {
				IMonitor mon = context.getMonitor();
				mon.worked(1);
			}
		} finally {
			hdf5Reader.close();
		}
	}
	
	private void exportASCII(IErrorDataset axis, IDataset data, IDataset errors, String fullName, String header, List<String> headings) throws ScanFileHolderException {
		String dataName = data.getName();
		IDataset[] columns = new IDataset[] {DatasetUtils.transpose(data, null)};
		if (axis != null) {
			if (axis.hasErrors()) {
				AbstractDataset axisErrors = DatasetUtils.cast((AbstractDataset) axis.getError(), ((AbstractDataset)data).getDtype());
				columns = (IDataset[]) ArrayUtils.addAll(new IDataset[]{axis, axisErrors}, columns);
				
			} else {
				columns = (IDataset[]) ArrayUtils.addAll(new IDataset[]{axis}, columns);
			}
			
		}
		if (errors != null) {
			columns = (IDataset[]) ArrayUtils.addAll(columns, new IDataset[]{DatasetUtils.transpose(errors, null)});
		}
		data = DatasetUtils.concatenate(columns, 1);
		data.setName(dataName);
		
		DataHolder dh = new  DataHolder();
		dh.addDataset(data.getName(), data);
		
		ASCIIDataWithHeadingSaver saver = new ASCIIDataWithHeadingSaver(fullName);
		saver.setCellFormat("%-12.8g");
		saver.setHeader(header);
		saver.setHeadings(headings);
		saver.saveFile(dh);
	}
	
	private void exportCanSAS(final ILazyDataset         lz, 
            final String               nameFrag,
            final IConversionContext   context) throws Exception {
		
		String selFilePath = context.getSelectedConversionFile().getAbsolutePath();
		IHierarchicalDataFile hdf5Reader = HierarchicalDataFactory.getReader(selFilePath);
		
		try {
			//get the x axis if required
			AbstractDataset axis = null;
			AbstractDataset axisErrors = null;
			String axisUnits = "a.u.";
			if (context.getAxisDatasetName() != null) {
				axis = (AbstractDataset)getAxis(context.getAxisDatasetName(), context.getSelectedConversionFile());
				axis.squeeze();
				axisUnits = getAxisUnit(context.getAxisDatasetName(), context.getSelectedConversionFile());
				if (axis.hasErrors()) {
					axisErrors = DatasetUtils.cast((AbstractDataset) ((IErrorDataset) axis).getError(),
							axis.getDtype());
					axisErrors.squeeze();
				}
			}
			
			//Set up position iterator (final 2 dimensions saved in a single file
			int[] stop = lz.getShape();
			boolean hasErrors = (lz.getLazyErrors() != null ? true : false);
			int iterDim = lz.getRank() - 1;
			int[] cutAxes = new int[] {lz.getRank() - 1};
			
			PositionIterator iterator = new PositionIterator(stop, cutAxes);
			
			for (int i = 0 ; i < iterDim ; i++) {
				stop[i] = 0;
			}
			
			int[] step = new int[stop.length];
			for (int i = 0 ; i < step.length; i++) {
				step[i] = 1;
			}
			
			ObjectFactory of       = new ObjectFactory();
			SASrootType   sasRoot  = of.createSASrootType();
			SASsampleType sasSample  = of.createSASsampleType();
			
			SASsourceType sasSource = of.createSASsourceType();
			sasSource.setRadiation("x-ray");
			SASdetectorType sasDetector = of.createSASdetectorType();
			sasDetector.setName(nameFrag);
			SAScollimationType sasCollimation = of.createSAScollimationType();
			
			SASinstrumentType sasInstrument  = of.createSASinstrumentType();
			sasInstrument.setName("Diamond Light Source Ltd.");
			sasInstrument.setSASsource(sasSource);
			sasInstrument.getSASdetector().add(sasDetector);
			sasInstrument.getSAScollimation().add(sasCollimation);
			SAStransmissionSpectrumType sasTransmission  = of.createSAStransmissionSpectrumType();
			
			try {
				Dataset titleData = (Dataset) hdf5Reader.getData(DEFAULT_TITLE_NODE);
				String[] str = (String[]) titleData.getData();
				if (str.length > 0) {
					String title = str[0];
					sasSample.setID(title);
				} else {
					sasSample.setID("N/A");
				}
			} catch (Exception e) {
				logger.info("Default title node {} was not found", DEFAULT_TITLE_NODE);
				sasSample.setID("N/A");
			}
			
			String pathToFolder = context.getOutputPath();
			String fileName = buildFileName(context.getSelectedConversionFile().getAbsolutePath(),nameFrag);
			String fullName = pathToFolder + File.separator + fileName + ".xml";
			
			//Iterate over lazy dataset and save
			while (iterator.hasNext()) {
				
				SASentryType  sasEntry = of.createSASentryType();
				
				int[] start = iterator.getPos();
				
				for (int j = 0 ; j < iterDim ; j++) {
					stop[j] = start[j]+1;
				}
				
				Slice[] slices = Slice.convertToSlice(start, stop, step);
				IDataset data = lz.getSlice(slices).squeeze();
				
				AbstractDataset errors = null;
				if (hasErrors) {
					errors = DatasetUtils.cast((AbstractDataset) ((IErrorDataset) data).getError(),
							((AbstractDataset)data).getDtype());
					errors.squeeze();
				}
				
				Run run = new Run();
				String runName = "Frame"+ nameStringFromSliceArray(iterDim, slices);
				run.setValue(runName);
				sasEntry.getRun().add(run);
				
				SASdataType sasData  = of.createSASdataType();
				
				PositionIterator iter = new PositionIterator(data.getShape(), new int[] {});
				while (iter.hasNext()) {
					int[] idx = iter.getPos();
					float val;
					
					IdataType iData = of.createIdataType();
					FloatUnitType I = of.createFloatUnitType();
					val = data.getFloat(idx);
					I.setValue(val);
					I.setUnit("a.u.");
					iData.setI(I);
					if (axis != null) {
						FloatUnitType Q = of.createFloatUnitType();
						val = axis.getFloat(idx);
						Q.setValue(val);
						Q.setUnit(axisUnits);
						iData.setQ(Q);
					}
					if (errors != null) {
						FloatUnitType devI = of.createFloatUnitType();
						val = errors.getFloat(idx);
						devI.setValue(val);
						devI.setUnit("a.u.");
						iData.setIdev(devI);
					}
					if (axisErrors != null) {
						FloatUnitType devQ = of.createFloatUnitType();
						val = axisErrors.getFloat(idx);
						devQ.setValue(val);
						devQ.setUnit(axisUnits);
						iData.setQdev(devQ);
					}
					sasData.getIdata().add(iData);
				}
				
				sasEntry.setTitle(data.getName());
				sasEntry.getSASdata().add(sasData);
				sasEntry.setSASsample(sasSample);
				sasEntry.getSAStransmissionSpectrum().add(sasTransmission);
				sasEntry.setSASinstrument(sasInstrument);
				sasEntry.getSASnote().add(selFilePath);
				
				sasRoot.getSASentry().add(sasEntry);
			}
			JAXBElement<SASrootType> jabxSASroot = of.createSASroot(sasRoot);
	
			JAXBContext jc = JAXBContext.newInstance(CANSAS_JAXB_CONTEXT);
			Marshaller m = jc.createMarshaller();
			m.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "http://www.cansas.org/formats/1.1/cansas1d.xsd");		
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			m.marshal(jabxSASroot, new FileOutputStream(fullName));
			
			if (context.getMonitor() != null) {
				IMonitor mon = context.getMonitor();
				mon.worked(1);
			}
		} finally {
			hdf5Reader.close();
		}
	}
	
	private String getAxisDatasetName(String axisDatasetName) {
		
		if (!(axisDatasetName.contains("/"))) {
			return DEFAULT_AXIS_NAME;
		} else {
			int pos = axisDatasetName.lastIndexOf("/");
			return axisDatasetName.substring(pos+1, axisDatasetName.length());
		}
	}

	private String nameStringFromSliceArray(int iterDim, Slice[] slices) {

		StringBuilder t = new StringBuilder();
		t.append('_');
		for (int idx = 0; idx < iterDim; idx++) {
			Slice s = slices[idx];
			t.append(s != null ? s.toString() : "");
			t.append('_');
		}
		t.deleteCharAt(t.length()-1);
		return t.toString();
	}
	
	private IDataset getAxis(String datasetName, File path) {
		
		IDataset data = null;
		try {
			data = LoaderFactory.getDataSet(path.getAbsolutePath(), datasetName, null);
			//expand so the concatenation works later
			data.setShape(data.getShape()[0],1);
			data.setName(getAxisDatasetName(datasetName));
		} catch (Exception e) {
			logger.warn("Couldn't get dataset: " + datasetName);
		}
		return data;
	}
	
	private String getAxisUnit(String datasetName, File path) {
		try {
			HDF5File tree = new HDF5Loader(path.getAbsolutePath()).loadTree();
			HDF5Node node = tree.findNodeLink(datasetName).getDestination();
			String units = null;
			if (node.containsAttribute("units")) {
				units = node.getAttribute("units").getFirstElement();
			} else if (node.containsAttribute("unit")) {
				units = node.getAttribute("unit").getFirstElement();
			}
			if (units != null) {
				UnitFormat unitFormat = UnitFormat.getUCUMInstance();
				String angstrom = unitFormat.format(NonSI.ANGSTROM.inverse());
				String nanometer = unitFormat.format(SI.NANO(SI.METER)
						.inverse());
				if (units.equals(nanometer)) {
					return "1/nm";
				} else if (units.equals(angstrom)) {
					return "1/A";
				}
			}
		} catch (ScanFileHolderException e) {
			logger.warn("Unit information for axis dataset {} not found", datasetName);
		}
		return "a.u.";
	}
	
	private String buildFileName(String pathToOriginal, String datasetName) {
		
		String name = new File(pathToOriginal).getName();
		int index = name.lastIndexOf('.');
		name = name.substring(0, index);
		
		if (datasetName.contains("processing")) {
			String trimmed = datasetName.replaceAll("(.*_processing/)", "");
			trimmed = trimmed.replaceAll("/data", "");
			name = name + "_" + trimmed;
		}
		return name;
	}
}
