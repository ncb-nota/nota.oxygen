package nota.oxygen.epub;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Element;

import nota.oxygen.common.Utils;
import ro.sync.ecss.extensions.api.AuthorAccess;
import ro.sync.ecss.extensions.api.access.AuthorWorkspaceAccess;
import ro.sync.ecss.extensions.api.node.AttrValue;
import ro.sync.ecss.extensions.api.node.AuthorElement;
import ro.sync.ecss.extensions.api.node.AuthorNode;
import ro.sync.exml.workspace.api.editor.WSEditor;
import ro.sync.exml.workspace.api.editor.page.WSEditorPage;
import ro.sync.exml.workspace.api.editor.page.author.WSAuthorEditorPage;

public class EpubUtils {
	
	private static Pattern EPUB_URL_REGEX = Pattern.compile("^(zip:file:[^!]+!/).+$");
	
	public static URL getEpubUrl(URL baseEpubUrl, String url)
	{
		Matcher m = EPUB_URL_REGEX.matcher(baseEpubUrl.toString());
		if (!m.matches()) return null;
		URL result;
		try {
			result = new URL(m.group(1));
			result = new URL(result, url);
			return result;
		} catch (MalformedURLException e) {
			return null;
		}
		
	}
	
	public static AuthorAccess getAuthorDocument(AuthorAccess authorAccess, URL docUrl)
	{
		AuthorWorkspaceAccess wa = authorAccess.getWorkspaceAccess();
		WSEditor editor = wa.getEditorAccess(docUrl);
		if (editor == null)
		{
			if (!wa.open(docUrl)) return null;
			editor = wa.getEditorAccess(docUrl);
			if (editor == null) return null;
		}
		if (editor.getCurrentPageID() != WSEditor.PAGE_AUTHOR) editor.changePage(WSEditor.PAGE_AUTHOR);
		WSEditorPage wep = editor.getCurrentPage();
		WSAuthorEditorPage aea = (wep instanceof WSAuthorEditorPage ? (WSAuthorEditorPage)wep : null);
		if (aea == null) return null;
		return aea.getAuthorAccess();
	}
	
	public static URL getPackageUrl(AuthorAccess authorAccess) {
		try {
			if (authorAccess == null) return null;
			URL docUrl = authorAccess.getDocumentController().getAuthorDocumentNode().getXMLBaseURL(); 
			AuthorAccess containerDocAccess = getAuthorDocument(authorAccess, getEpubUrl(docUrl, "META-INF/container.xml"));
			if (containerDocAccess == null) return null;
			Element rootElem = Utils.deserializeElement(Utils.serialize(
					containerDocAccess, 
					containerDocAccess.getDocumentController().getAuthorDocumentNode()));
			containerDocAccess.getEditorAccess().close(true);
			XPath xp = Utils.getXPath("ns", "urn:oasis:names:tc:opendocument:xmlns:container");
			try {
				String relUrl = xp.evaluate("//ns:rootfile[@media-type='application/oebps-package+xml']/@full-path", rootElem); 
				return getEpubUrl(docUrl, relUrl);
			} catch (XPathExpressionException e) {
				return null;
			}
		}
		catch (Exception e)
		{
			return null;
		}
	}

	public static AuthorAccess getPackageItemDocumentBySPath(AuthorAccess opfAccess, String xpath) {
		try {
			AuthorNode[] res = opfAccess.getDocumentController().findNodesByXPath(xpath, true, true, true);
			if (res.length > 0)
			{
				AttrValue itemHref = ((AuthorElement)res[0]).getAttribute("href");
				if (itemHref != null)
				{
					return getAuthorDocument(
							opfAccess, 
							new URL(opfAccess.getEditorAccess().getEditorLocation(), itemHref.getValue()));
				}
			}
		}
		catch (Exception e) {
			return null;
		}
		return null;
	}

	public static  AuthorAccess getXHTMLNavDocument(AuthorAccess opfAccess) {
		return getPackageItemDocumentBySPath(opfAccess, "//item[@media-type='application/xhtml+xml' and @properties='nav']");
	}

	public static  AuthorAccess getNCXDocument(AuthorAccess opfAccess) {
		return getPackageItemDocumentBySPath(opfAccess, "//item[@media-type='application/x-dtbncx+xml']");
	}

}
