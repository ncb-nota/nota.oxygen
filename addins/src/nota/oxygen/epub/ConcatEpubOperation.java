package nota.oxygen.epub;

import java.net.URL;

import javax.swing.JTextArea;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ro.sync.ecss.extensions.api.ArgumentDescriptor;
import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorAccess;
import ro.sync.ecss.extensions.api.AuthorDocumentController;
import ro.sync.ecss.extensions.api.AuthorOperationException;
import ro.sync.ecss.extensions.api.node.AttrValue;
import ro.sync.ecss.extensions.api.node.AuthorElement;
import ro.sync.exml.workspace.api.editor.page.text.WSTextEditorPage;
import nota.oxygen.common.BaseAuthorOperation;
import nota.oxygen.common.Utils;

public class ConcatEpubOperation extends BaseAuthorOperation {
	private String epubFilePath = "";

	@Override
	public ArgumentDescriptor[] getArguments() {
		return new ArgumentDescriptor[]{};
	}

	@Override
	public String getDescription() {
		return "Concats epub files";
	}

	@Override
	protected void parseArguments(ArgumentsMap args) throws IllegalArgumentException {
		// Nothing to parse!!!
	}
	
	@Override
	protected void doOperation() throws AuthorOperationException {	
		String fileName = "", fileEpubType = "", dcIdentifier = "";
		try {
			// get epub folder path
			epubFilePath = EpubUtils.getEpubFolder(getAuthorAccess());
			if (epubFilePath.equals("")) {
				showMessage("Could not access epub folder");
				return;
			}
			
			// get dc:identifier value from package file
			AuthorDocumentController opfCtrl = getAuthorAccess().getDocumentController();
			opfCtrl.beginCompoundEdit();
			AuthorElement metaDcIdentifier = getFirstElement(opfCtrl.findNodesByXPath(String.format("/package/metadata/dc:identifier"), true, true, true));
			if (metaDcIdentifier != null) {
				dcIdentifier = metaDcIdentifier.getTextContent();
				
			}
			opfCtrl.cancelCompoundEdit();
			
			// construct a new document
			Document doc = EpubUtils.createDocument();
			if (doc == null) {
				showMessage("Could not construct new document");
				return;
			}
			
			// get all xhtml files in epub (besides nav.html)
			URL[] xhtmlUrls = EpubUtils.getSpineUrls(getAuthorAccess(), true);
			if (xhtmlUrls.length < 2) {
				showMessage("This epub cannot be concatenated (only one xhtml file)");
				return;
			}
			
			Element htmlElementAdded = (Element) doc.createElement("html");
			Element headElementAdded = (Element) doc.createElement("head");
			Element bodyElementAdded = (Element) doc.createElement("body");
			
			// traverse each xhtml document in epub
			for (URL xhtmlUrl : xhtmlUrls) {
				fileName = getAuthorAccess().getUtilAccess().getFileName(xhtmlUrl.toString());
				fileEpubType = fileName.substring(fileName.lastIndexOf("-") + 1, fileName.lastIndexOf("."));
				
				// check for non spine elements
				if(fileName.equals("nav.xhtml") || !fileName.substring(fileName.lastIndexOf(".")).equals(".xhtml")) {
					// continue if non spine elements
					continue;
				}
				
				// get xml from each xhtml document
				WSTextEditorPage editorPage = EpubUtils.getTextDocument(getAuthorAccess(), xhtmlUrl);
				JTextArea textArea = (JTextArea) ((WSTextEditorPage) editorPage).getTextComponent();
				String docText = textArea.getText();
				if (docText == null) {
					showMessage("Could not get xml from xhtml document");
					return;
				}
				
				// deserialize xml
				Node html = Utils.deserializeElement(docText);
				
				// add html attributes
				NamedNodeMap htmlAttributes = html.getAttributes();
				for (int j = 0; j < htmlAttributes.getLength(); j++) {
					Attr attribute = (Attr) htmlAttributes.item(j);
					htmlElementAdded.setAttributeNS(attribute.getNamespaceURI(), attribute.getName(), attribute.getValue());
				}

				// traverse html nodes
				NodeList htmlNodes = html.getChildNodes();
				for (int i = 0; i < htmlNodes.getLength(); i++) {
					Node htmlNode = htmlNodes.item(i);
					if (htmlNode.getNodeName().equals("head")) {
						// get head nodes
						NodeList headNodes = htmlNode.getChildNodes();
						NodeList headNodesAdded = headElementAdded.getChildNodes();

						// append head elements
						for (int j = 0; j < headNodes.getLength(); j++) {
							Node headNode = doc.importNode(headNodes.item(j), true);
							String metaValue = EpubUtils.getMetaNodeValue(headNode);

							// check if head element not already exists
							boolean exists = false;
							for (int k = 0; k < headNodesAdded.getLength(); k++) {
								Node headNodeAdded = headNodesAdded.item(k);
								String metaValueAdded = EpubUtils.getMetaNodeValue(headNodeAdded);

								if (headNodeAdded.isEqualNode(headNode)) exists = true;
								else if (!metaValue.equals("")	&& !metaValueAdded.equals("") && metaValue.equals(metaValueAdded)) exists = true;
							}

							// append head element
							if (!exists) {
								if (headNode.getNodeName().equalsIgnoreCase("meta")) {
									if (headNode.getAttributes().getNamedItem("name") != null) {
										String value = String.valueOf(headNode.getAttributes().getNamedItem("name").getNodeValue());
										if (value.equals("dc:identifier") && !dcIdentifier.equals("")) {
											if (headNode.getAttributes().getNamedItem("content") != null) {
												headNode.getAttributes().getNamedItem("content").setNodeValue(dcIdentifier);
											}
										}
									}
								}
								
								headElementAdded.appendChild(headNode);
							}
						}
					}

					if (htmlNode.getNodeName().equals("body")) {
						// create new section
						Element sectionElement = (Element) doc.createElement("section");

						// append body attributes to new section
						NamedNodeMap bodyAttributes = htmlNode.getAttributes();
						
						for (int j = 0; j < bodyAttributes.getLength(); j++) {
							Attr attr = (Attr) bodyAttributes.item(j);
							String attrName = attr.getName();
							String attrValue = attr.getValue();
							if (attrName.equals("epub:type")) {
								String[] epubTypes = attrValue.split("\\ ");
								boolean epubTypeOk = false;
								for (String epubType : epubTypes) {
									if (!epubType.equals("frontmatter") && !epubType.equals("bodymatter")  && !epubType.equals("backmatter") && !epubType.equals("rearmatter")) {
										epubTypeOk = true;
									}
								}
								
								if (!epubTypeOk && !fileEpubType.equals("") && !fileEpubType.equals("frontmatter") && !fileEpubType.equals("bodymatter")  && !fileEpubType.equals("backmatter") && !fileEpubType.equals("rearmatter")) {
									attrValue = attrValue + " " + fileEpubType;
								}
							}
							sectionElement.setAttributeNS(attr.getNamespaceURI(), attrName, attrValue);
						}
						
						// append body elements
						NodeList bodyNodes = htmlNode.getChildNodes();
						for (int j = 0; j < bodyNodes.getLength(); j++) {
							sectionElement.appendChild(doc.importNode(bodyNodes.item(j),	true));
						}

						// get all references
						NodeList refNodes = sectionElement.getElementsByTagName("a");
						for (int j = 0; j < refNodes.getLength(); j++) {
							Node refNode = refNodes.item(j);
							NamedNodeMap attrs = refNode.getAttributes();
							for (int k=0; k<attrs.getLength(); k++) {
								Attr attr = (Attr) attrs.item(k);
								if (attr.getNodeName().equalsIgnoreCase("href")) {
									if (!attr.getNodeValue().contains("www") && attr.getNodeValue().contains("#")) {
										// remove file reference
										attr.setNodeValue(attr.getNodeValue().substring(attr.getNodeValue().indexOf("#")));
									}
									else if (!attr.getNodeValue().contains("www") && !attr.getNodeValue().contains("#") && attr.getNodeValue().contains(".xhtml")) {
										String fileRef = attr.getNodeValue();
										AuthorAccess fileRefAccess = EpubUtils.getAuthorDocument(getAuthorAccess(), new URL(epubFilePath + "/" + fileRef));
										// add unique ids to missing elements
										if (!EpubUtils.addUniqueIds(fileRefAccess)) {
											showMessage(EpubUtils.ERROR_MESSAGE);
											return;
										}
										
										AuthorDocumentController fileRefCtrl = fileRefAccess.getDocumentController();
										fileRefCtrl.beginCompoundEdit();
										AuthorElement bodyId = getFirstElement(fileRefCtrl.findNodesByXPath("/html/body", true, true, true));
										if (bodyId != null) {
											AttrValue id = bodyId.getAttribute("id");
											attr.setNodeValue("#" + id.getValue());
										}
										fileRefCtrl.cancelCompoundEdit();

										fileRefAccess.getEditorAccess().close(true);
									}
								}
							}
						}

						// append section to body element
						bodyElementAdded.appendChild(sectionElement);
					}
				}
			}
			
			htmlElementAdded.appendChild(headElementAdded);
			htmlElementAdded.appendChild(bodyElementAdded);
			doc.appendChild(htmlElementAdded);
			
			// save new concatenated xhtml document
			if (!EpubUtils.saveDocument(getAuthorAccess(), doc, new URL(epubFilePath + "/" + EpubUtils.CONCAT_FILENAME))) {
				showMessage(EpubUtils.ERROR_MESSAGE);
				return;
			}
			
			// add xhtml document to opf document
			if (!EpubUtils.addOpfItem(getAuthorAccess(), EpubUtils.CONCAT_FILENAME, true)) {
				showMessage(EpubUtils.ERROR_MESSAGE);
				return;
			}

			// add unique ids to missing elements
			AuthorAccess xhtmlAccess = EpubUtils.getAuthorDocument(getAuthorAccess(), new URL(epubFilePath + "/" + EpubUtils.CONCAT_FILENAME));
			if (!EpubUtils.addUniqueIds(xhtmlAccess)) {
				showMessage(EpubUtils.ERROR_MESSAGE);
				return;
			}
			
			// clean up
			for (URL xhtmlUrl : xhtmlUrls) {
				fileName = getAuthorAccess().getUtilAccess().getFileName(xhtmlUrl.toString());
				
				// check for non spine elements
				if(fileName.equals("nav.xhtml") || !fileName.substring(fileName.lastIndexOf(".")).equals(".xhtml")) {
					// remove fallback from non xhtml spine elements
					// remove non spine elements from spine
					if (!EpubUtils.removeFallbackFromOpf(getAuthorAccess(), fileName)) {
						showMessage(EpubUtils.ERROR_MESSAGE);
						return;
					}
					
					// continue if spine elements is not xhtml
					continue;
				}

				// delete xhtml document
				getAuthorAccess().getWorkspaceAccess().delete(xhtmlUrl);
				
				// remove xhtml document from opf document
				if (!EpubUtils.removeOpfItem(getAuthorAccess(), getAuthorAccess().getUtilAccess().getFileName(xhtmlUrl.toString()))) {
					showMessage(EpubUtils.ERROR_MESSAGE);
					return;
				}
			}
			
			// save opf
			getAuthorAccess().getEditorAccess().save();
			
			// update navigation documents
			if (!EpubUtils.updateNavigationDocuments(getAuthorAccess())) {
				showMessage(EpubUtils.ERROR_MESSAGE);
				return;
			}
			
			EpubUtils.getNCXDocument(getAuthorAccess()).getEditorAccess().save();;
			EpubUtils.getXHTMLNavDocument(getAuthorAccess()).getEditorAccess().save();
			getAuthorAccess().getWorkspaceAccess().closeAll();
		} catch (Exception e) {
			e.printStackTrace();
			showMessage("Could not finalize operation - an error occurred in file (" + fileName + "): " + e.getMessage());
			return;
		}
	}
}
