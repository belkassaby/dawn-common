package org.dawnsci.persistence.workflow.xml;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

/**
 * Class used to read a workflow file with its _workbenchVersion. 
 * If it is less than 1.4 then remove all attributes called "Expression Mode".
 * 
 * @author wqk87977
 *
 */
public class MomlUpdater {

	private static final Logger Logger = LoggerFactory.getLogger(MomlUpdater.class);
	private static final String EXPRESSION_MODE = "Expression Mode";

	/**
	 * Updates the Moml file by deleting its expression mode properties if necessary.
	 * @param filePath
	 *          The full file Path of the Moml file
	 * @return String
	 *          The updated moml file as a string
	 */
	public static String updateMoml(String filePath) {
		try {
			File fXmlFile = new File(filePath);

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

			Document doc = dBuilder.parse(fXmlFile);
			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName(doc.getDocumentElement().getNodeName());
			Node baseNode = nList.item(0);
			NodeList nodes = baseNode.getChildNodes();

			// parse through each node of the base node (entities)
			for (int temp = 0; temp < nodes.getLength(); temp++) {
				Node nNode = nodes.item(temp);
				NodeList nNodeChildren = nNode.getChildNodes();
				// parse through each node of each entity
				for (int i = 0; i < nNodeChildren.getLength(); i++) {
					Node aNode = nNodeChildren.item(i);
					if (aNode.getNodeType() == Node.ELEMENT_NODE) {
						Element eElement = (Element) aNode;
						Attr attribName = eElement.getAttributeNode("name");
						// remove the expression mode node
						if (attribName.getValue().equals(EXPRESSION_MODE)) {
							nNode = nNode.removeChild(aNode);
						}
					}
				}
			}
			// return the resulting XML tree as a String
			return printXMLTree(baseNode);

		} catch (ParserConfigurationException e) {
			Logger.error("ParserConfigurationException:"+e);
			return null;
		} catch (SAXException e) {
			Logger.error("SAXException:"+e);
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			Logger.error("IOException:"+e);
			return null;
		}
	}

	/**
	 * Method that returns a DOM node as a String
	 * @param node
	 * @return a string
	 */
	public static String printXMLTree(Node node) {
		DOMImplementationLS lsImpl = (DOMImplementationLS)node.getOwnerDocument().getImplementation().getFeature("LS", "3.0");
		LSSerializer serializer = lsImpl.createLSSerializer();
		serializer.getDomConfig().setParameter("xml-declaration", true); //by default its true, so set it to false to get String without xml-declaration
		return serializer.writeToString(node);
	}
}
