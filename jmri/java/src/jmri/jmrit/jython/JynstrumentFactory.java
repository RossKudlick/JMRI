package jmri.jmrit.jython;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

import org.python.util.PythonInterpreter;

/**
 * A JynstrumentFactory handles instantiation and 
 * connection of {@link Jynstrument} instances
 *
 * @see Jynstrument
 * @author Lionel Jeanson  Copyright 2009
 * @since 2.7.8
 */
public class JynstrumentFactory {
	private static final String instanceName = "jynstrumentObjectInstance";

	public static Jynstrument createInstrument(String path, Object context) {
		String className = validate(path);
		if (className == null) {
			// Try containing directory
			File f = new File(path);
			path = f.getParent();
			className = validate(path);
			if (className == null) {
				log.error("Invalid instrument");
				return null;
			}
		}
		String jyFile = path+ File.separator + className+".py" ;
		PythonInterpreter interp = jmri.util.PythonInterp.getPythonInterpreter();
		Jynstrument jyns;
		try {
			interp.execfile(jyFile);
			interp.exec(instanceName+" = "+className+"()");		// instantiate one
			jyns = interp.get(instanceName, Jynstrument.class); // get it
			interp.exec("del "+instanceName);  // delete reference in Jython interpreter
		}
		catch (Exception e) {
			log.error("Exception while creating Jynstrument: "+e);
			return null;
		}
		jyns.setClassName(className);
		jyns.setContext(context);
		if (! jyns.validateContext() )	{  // check validity of this Jynstrument for that extended context
			log.error("Invalid context for Jynstrument, host is "+context.getClass()+ " and "+jyns.getExpectedContextClassName()+" kind of host is expected");
			return null;
		}
		jyns.setJythonFile(jyFile);
		jyns.setFolder(path);
		jyns.setPopUpMenu(new JynstrumentPopupMenu(jyns));
		jyns.init();  // GO!
		return jyns;
	}

	// validate Jynstrument path, return className
	private static String validate(String path) {
		if (path == null)
			return null;
		if ( path.length() - 4 < 0) {
			log.error("File name too short");
			return null;
		}
		File f = new File(path);

		// Path must be a folder named xyz.jin
		if ( ! f.isDirectory())  {
			log.debug("Not a directory, trying parent");
			return null; 
		}
		if ( path.substring( path.length() - 4).compareToIgnoreCase(".jyn") != 0 ) {
			log.debug("Not an instrument");
			return null;
		}

		// must contain a xyz.py file and construct class name from filename (xyz actually) xyz class in xyz.py file in xyz.jin folder
		String[] children = f.list();
		String className = null;
		String assumedClassName = f.getName().substring(0, f.getName().length()-4);
		for (int i=0; i<children.length; i++)
			if ((children[i]).compareToIgnoreCase(assumedClassName+".py") == 0)
				return assumedClassName; // got exact match for folder name			
			else 
				if (children[i].substring(children[i].length() - 3).compareToIgnoreCase(".py") == 0 )
					className = children[i].substring(0, children[i].length() - 3); // else take whatever comes

		return className;
	}

	static Logger log = LoggerFactory.getLogger(JynstrumentFactory.class.getName());
}
