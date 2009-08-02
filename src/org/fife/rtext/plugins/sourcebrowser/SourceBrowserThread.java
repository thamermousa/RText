/*
 * 02/14/2006
 *
 * SourceBrowserThread.java - Runs the source browser executable in a separate
 * thread and constructs the source browser information after its run.
 * Copyright (C) 2006 Robert Futrell
 * robert_futrell at users.sourceforge.net
 * http://rtext.fifesoft.com
 *
 * This file is a part of RText.
 * 
 * RText is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * RText is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.fife.rtext.plugins.sourcebrowser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.tree.*;

import org.fife.ctags.TagEntry;
import org.fife.io.ProcessRunner;
import org.fife.rtext.RTextEditorPane;
import org.fife.ui.GUIWorkerThread;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;


/**
 * A thread that actually runs the ctags executable and organizes its output
 * into a data structure for the GUI to display.  All work is done in this
 * thread to keep the GUI from freezing.
 *
 * @author Robert Futrell
 * @version 1.0
 */
class SourceBrowserThread extends GUIWorkerThread implements SyntaxConstants {

	private HashMap map;	// Hash map of array lists (tag type=>array list).
	private SourceBrowserPlugin plugin;
	private ArrayList[] arrayListBuffer;	// Cached array lists for performance.

	private int maxTime;	// Milliseconds to wait for ctags to return.
	private RTextEditorPane textArea;
	private String style;
	private String language;

	private static final int MAX_NUM_HASH_MAPS = 12;	// Longest string length in tagTypesMap.
	private static final Map tagTypesMap;

	static {

		tagTypesMap = new HashMap();
		tagTypesMap.put(SYNTAX_STYLE_NONE,				"");
		tagTypesMap.put(SYNTAX_STYLE_ASSEMBLER_X86,		"");
		tagTypesMap.put(SYNTAX_STYLE_C,				"cdfgmnstv");
		tagTypesMap.put(SYNTAX_STYLE_CPLUSPLUS,			"cdfgmnstv");
		tagTypesMap.put(SYNTAX_STYLE_CSHARP,			"cdEfgimnpqst");
		tagTypesMap.put(SYNTAX_STYLE_CSS,				"");
		tagTypesMap.put(SYNTAX_STYLE_FORTRAN,			"bcefklmnpstv");
		tagTypesMap.put(SYNTAX_STYLE_GROOVY,			"");
		tagTypesMap.put(SYNTAX_STYLE_HTML,				"af");
		tagTypesMap.put(SYNTAX_STYLE_JAVA,				"cfimp");
		tagTypesMap.put(SYNTAX_STYLE_JAVASCRIPT,		"f");
		tagTypesMap.put(SYNTAX_STYLE_JSP,				"");
		tagTypesMap.put(SYNTAX_STYLE_LISP,				"f");
		tagTypesMap.put(SYNTAX_STYLE_LUA,				"f");
		tagTypesMap.put(SYNTAX_STYLE_MAKEFILE,			"m");
		tagTypesMap.put(SYNTAX_STYLE_PERL,				"cls");
		tagTypesMap.put(SYNTAX_STYLE_PHP,				"cidfvj");
		tagTypesMap.put(SYNTAX_STYLE_PROPERTIES_FILE,	"");
		tagTypesMap.put(SYNTAX_STYLE_PYTHON,			"cfm");
		tagTypesMap.put(SYNTAX_STYLE_RUBY,				"cfmF");
		tagTypesMap.put(SYNTAX_STYLE_SAS,				"");
		tagTypesMap.put(SYNTAX_STYLE_SQL,				"cfFLPprstTv");
		tagTypesMap.put(SYNTAX_STYLE_TCL,				"cmp");
		tagTypesMap.put(SYNTAX_STYLE_UNIX_SHELL,		"f");
		tagTypesMap.put(SYNTAX_STYLE_WINDOWS_BATCH,		"");
		tagTypesMap.put(SYNTAX_STYLE_XML,				"");

	}

	/**
	 * Constructor.
	 *
	 * @param plugin The soruce browser plugin.
	 */
	public SourceBrowserThread(SourceBrowserPlugin plugin) {

		this.plugin = plugin;
		map = new HashMap();

		// Create a cache of array lists to use for performance.
		arrayListBuffer = new ArrayList[MAX_NUM_HASH_MAPS];
		for (int i=0; i<MAX_NUM_HASH_MAPS; i++) {
			arrayListBuffer[i] = new ArrayList();
		}

	}


	/**
	 * Adds a node to the ctags tree containing one child for each tag entry
	 * passed in.
	 *
	 * @param root The root of the ctags tree.
	 * @param title The title for this node.
	 * @param contents An array of <code>org.fife.ctags.TagEntry</code>s and
	 *        <code>String</code>s to add as children of this node.
	 */
	private static void addTagTypeNode(DefaultMutableTreeNode root,
							String title, Object contents) {
		addTagTypeNode(root, title, contents, null);
	}


	/**
	 * Adds a node to the ctags tree containing one child for each tag entry
	 * passed in.
	 *
	 * @param root The root of the ctags tree.
	 * @param title The title for this node.
	 * @param contents An array of <code>org.fife.ctags.TagEntry</code>s and
	 *        <code>String</code>s to add as children of this node.
	 * @param icon The icon for this node's children.  This may be
	 *        <code>null</code>.
	 */
	private static void addTagTypeNode(DefaultMutableTreeNode root,
						String title, Object contents, Icon icon) {

		List contentsList = (List)contents;
		GroupTreeNode node = new GroupTreeNode(icon);

		int size = 0;
		if (contentsList!=null) {
			size = contentsList.size();
			for (int i=0; i<size; i++) {
				node.add(new DefaultMutableTreeNode(contentsList.get(i)));
			}
		}

		node.setUserObject(title + " (" + size + ")");
		root.add(node);

	}


	/**
	 * Runs the ctags executable.
	 *
	 * @return A <code>TreeNode</code> object for the source browser tree.
	 *         If something goes wrong, this value will be <code>null</code>.
	 */
	public Object construct() {

		// Create data structures in which we can store the tags.
		map.clear();
		String knownTagTypes = (String)tagTypesMap.get(style);
		int count = knownTagTypes.length();
		for (int i=0; i<count; i++) {
			arrayListBuffer[i].clear();
			String tagType = knownTagTypes.substring(i,i+1);
			map.put(tagType, arrayListBuffer[i]);
		}

		// Create a command line to run ctags.
		String sourceFile = textArea.getFileFullPath();
		boolean exuberant = plugin.getCTagsType().equals(
							SourceBrowserPlugin.CTAGS_TYPE_EXUBERANT);
		//System.out.println("DEBUG: Exuberant: " + exuberant);
		String[] commandLine = null;
		if (exuberant) {
			commandLine = new String[5];
			commandLine[0] = plugin.getCTagsExecutableLocation();
			commandLine[1] = "-f";
			commandLine[2] = "-";
			commandLine[3] = "--language-force=" + language;
			commandLine[4] = sourceFile;
		}
		else { // standard
			commandLine = new String[2];
			commandLine[0] = plugin.getCTagsExecutableLocation();
			commandLine[1] = sourceFile;
		}

		// Run the process and collect its output in a separate thread.
		// If the thread does not complete in maxTime milliseconds, we'll
		// assume the process is a runaway one and we'll kill it.
		ProcessRunner runner = new ProcessRunner(commandLine);
		Thread t = new Thread(runner);
		t.start();
		try {
			t.join(maxTime);
		} catch (InterruptedException ie) {
			// Most likely interrupted because we were parsing a very large
			// file, above but the user clicked on a different tab before we
			// completed.  Don't print a stack trace; just interrupt the
			// thread doing the parsing (since we can't watch it anymore)
			// and return.
			t.interrupt();
			return null;
		}
		if (t.isAlive()) {
			// If it's still alive, we assume it's a runaway process.
			// This happens when the t.join(maxTime) above does not complete
			// before maxTime is up.
			t.interrupt();
			t = null;
			String s = plugin.getBundle().getString("Error.RunawayProcess");
			return new DefaultMutableTreeNode(s);
		}
		else if (runner.getLastError()!=null) {
			// If we got an error launching/running the process (such as
			// "not a valid win32 process", etc.), say so.
			String s = plugin.getBundle().getString("Error.RunningProcess");
			return new DefaultMutableTreeNode(s);
		}
		t = null;

		// Add stuff from the process's stdout to our maps.
		BufferedReader r = null;
		if (exuberant) {
			String stdout = runner.getStdout();
			r = new BufferedReader(new StringReader(stdout));
		}
		else {
			File file = new File("tags");
			if (!file.isFile()) {
				// TODO: Give better error message here - and localize me!
				String s = "tags file not found!";
				return new DefaultMutableTreeNode(s);
			}
			try {
				r = new BufferedReader(new FileReader(file));
			} catch (IOException ioe) {
				// If we got an error launching/running the process (such as
				// "not a valid win32 process", etc.), say so.
				ioe.printStackTrace();
				String s = plugin.getBundle().getString("Error.RunningProcess");
				return new DefaultMutableTreeNode(s);
			}
		}
		String line = null;
		try {
			while ((line=r.readLine()) != null) {
				TagEntry entry = new SourceBrowserPlugin.ExtendedTagEntry(line);
				ArrayList list = (ArrayList)map.get(entry.kind);
				if (list!=null) {	// A supported tag type for this language.
					list.add(entry);
				}
			}
			r.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
			// Don't return null, just return what we got.
		}

		// Sets the tree stuff.
		DefaultMutableTreeNode root = new DefaultMutableTreeNode(
										textArea.getFileName());
		if (SYNTAX_STYLE_C.equals(style) || 
				SYNTAX_STYLE_CPLUSPLUS.equals(style)) {
			addTagTypeNode(root, "Classes", map.get("c"));
			addTagTypeNode(root, "Macros", map.get("d"));
			addTagTypeNode(root, "Functions", map.get("f"));
			addTagTypeNode(root, "Enumerations", map.get("g"));
			addTagTypeNode(root, "Class/Struct/Union members", map.get("m"));
			addTagTypeNode(root, "Namespaces", map.get("n"));
			addTagTypeNode(root, "Structs", map.get("s"));
			addTagTypeNode(root, "Typedefs", map.get("t"));
			addTagTypeNode(root, "Variables", map.get("v"));
		}
		else if (SYNTAX_STYLE_CSHARP.equals(style)) {
			addTagTypeNode(root, "Classes", map.get("c"));
			addTagTypeNode(root, "Macros", map.get("d"));
			addTagTypeNode(root, "Events", map.get("E"));
			addTagTypeNode(root, "Fields", map.get("f"));
			addTagTypeNode(root, "Enumerations", map.get("g"));
			addTagTypeNode(root, "Interfaces", map.get("i"));
			addTagTypeNode(root, "Methods", map.get("m"));
			addTagTypeNode(root, "Namespaces", map.get("n"));
			addTagTypeNode(root, "Properties", map.get("p"));
			addTagTypeNode(root, "Structs", map.get("s"));
			addTagTypeNode(root, "Typedefs", map.get("t"));
		}
		else if (SYNTAX_STYLE_FORTRAN.equals(style)) {
			addTagTypeNode(root, "Block Data", map.get("b"));
			addTagTypeNode(root, "Common Blocks", map.get("c"));
			addTagTypeNode(root, "Entry Points", map.get("e"));
			addTagTypeNode(root, "Functions", map.get("f"));
			addTagTypeNode(root, "Type and Structure Components", map.get("k"));
			addTagTypeNode(root, "Labels", map.get("l"));
			addTagTypeNode(root, "Modules", map.get("m"));
			addTagTypeNode(root, "Namelists", map.get("n"));
			addTagTypeNode(root, "Programs", map.get("p"));
			addTagTypeNode(root, "Subroutines", map.get("s"));
			addTagTypeNode(root, "Derived Types/Structures", map.get("t"));
			addTagTypeNode(root, "Global and Module Variables", map.get("v"));
		}
		else if (SYNTAX_STYLE_HTML.equals(style)) {
			addTagTypeNode(root, "Named Anchors", map.get("a"));
			addTagTypeNode(root, "JavaScript functions", map.get("f"));
		}
		else if (SYNTAX_STYLE_JAVA.equals(style)) {
			addTagTypeNode(root, "Classes", map.get("c"));
			addTagTypeNode(root, "Fields", map.get("f"));
			addTagTypeNode(root, "Interfaces", map.get("i"));
			addTagTypeNode(root, "Methods", map.get("m"));
			addTagTypeNode(root, "Packages", map.get("p"));
		}
		else if (SYNTAX_STYLE_JAVASCRIPT.equals(style)) {
			addTagTypeNode(root, "Functions", map.get("f"));
		}
		else if (SYNTAX_STYLE_LISP.equals(style)) {
			addTagTypeNode(root, "Functions", map.get("f"));
		}
		else if (SYNTAX_STYLE_LUA.equals(style)) {
			addTagTypeNode(root, "Functions", map.get("f"));
		}
		else if (SYNTAX_STYLE_MAKEFILE.equals(style)) {
			addTagTypeNode(root, "Macros", map.get("m"));
		}
		else if (SYNTAX_STYLE_PERL.equals(style)) {
			addTagTypeNode(root, "Classes", map.get("c"));
			addTagTypeNode(root, "Labels", map.get("l"));
			addTagTypeNode(root, "Subroutines", map.get("s"));
		}
		else if (SYNTAX_STYLE_PHP.equals(style)) {
			addTagTypeNode(root, "Classes", map.get("c"));
			addTagTypeNode(root, "Interfaces", map.get("i"));
			addTagTypeNode(root, "Constants", map.get("d"));
			addTagTypeNode(root, "Functions", map.get("f"));
			addTagTypeNode(root, "Variables", map.get("v"));
			addTagTypeNode(root, "JavaScript Functions", map.get("j"));
		}
		else if (SYNTAX_STYLE_PYTHON.equals(style)) {
			addTagTypeNode(root, "Classes", map.get("c"));
			addTagTypeNode(root, "Functions", map.get("f"));
			addTagTypeNode(root, "Class Members", map.get("m"));
		}
		else if (SYNTAX_STYLE_RUBY.equals(style)) {
			addTagTypeNode(root, "Classes", map.get("c"));
			addTagTypeNode(root, "Methods", map.get("f"));
			addTagTypeNode(root, "Modules", map.get("m"));
			addTagTypeNode(root, "Singleton Methods", map.get("F"));
		}
		else if (SYNTAX_STYLE_SQL.equals(style)) {
			addTagTypeNode(root, "Cursors", map.get("c"));
			addTagTypeNode(root, "Functions", map.get("f"));
			addTagTypeNode(root, "Record Fields", map.get("F"));
			addTagTypeNode(root, "Block Label", map.get("L"));
			addTagTypeNode(root, "Packages", map.get("P"));
			addTagTypeNode(root, "Procedures", map.get("p"));
			addTagTypeNode(root, "Records", map.get("r"));
			addTagTypeNode(root, "Subtypes", map.get("s"));
			addTagTypeNode(root, "Tables", map.get("t"));
			addTagTypeNode(root, "Triggers", map.get("T"));
			addTagTypeNode(root, "Variables", map.get("V"));
		}
		else if (SYNTAX_STYLE_TCL.equals(style)) {
			addTagTypeNode(root, "Classes", map.get("c"));
			addTagTypeNode(root, "Methods", map.get("m"));
			addTagTypeNode(root, "Procedures", map.get("p"));
		}
		else if (SYNTAX_STYLE_UNIX_SHELL.equals(style)) {
			language = "Sh";
			addTagTypeNode(root, "Functions", map.get("f"));
		}

		return root;

	}


	/**
	 * Called on the event dispatching thread (not on the worker thread)
	 * after the <code>construct</code> method has returned.<p>
	 *
	 * This method should be overridden to do any work with the value
	 * returned from <code>get</code> to prevent deadlock.
	 */
	public void finished() {
		plugin.setTreeRoot((TreeNode)get());
	}


	/**
	 * Runs this thread with the specified parameters.
	 *
	 * @param maxTime The maximum amount of time to run this thread before
	 *        giving up.
	 * @param textArea The text area containing the file we're parsing.
	 * @param style The programming language to parse the file with.
	 * @param language The programming language of the file to parse.
	 */
	public void start(int maxTime, RTextEditorPane textArea, String style,
					String language) {
		this.maxTime = maxTime;
		this.textArea = textArea;
		this.style = style;
		this.language = language;
		super.start();
	}


}