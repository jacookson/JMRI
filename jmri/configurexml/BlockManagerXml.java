// BlockManagerXml.java

package jmri.configurexml;

import jmri.BeanSetting;
import jmri.Block;
import jmri.BlockManager;
import jmri.InstanceManager;
import jmri.Path;
import jmri.Sensor;
import jmri.Turnout;

import java.util.List;

import org.jdom.Element;

/**
 * Persistency implementation for BlockManager persistance.
 * <P>
 * The Block objects are not yet read in, pending a reliable write out!
 * <p>
 * Every block is written twice.  First, the list of blocks is written
 * without contents, so that we're sure they're all created on read-back.
 * Then, they're written out again with contents, including the block references
 * in the path elements.
 *
 * @author Bob Jacobsen Copyright: Copyright (c) 2008
 * @version $Revision: 1.20 $
 * @since 2.1.2
 *
 */
public class BlockManagerXml extends jmri.managers.configurexml.AbstractMemoryManagerConfigXML {

    public BlockManagerXml() {
    }

    /**
     * Subclass provides implementation to create the correct top
     * element, including the type information.
     * Default implementation is to use the local class here.
     * @param memories The top-level element being created
     */
    public void setStoreElementClass(Element memories) {
        memories.setAttribute("class","jmri.configurexml.BlockManagerXml");
    }

    /**
     * Store the contents of a BlockManager.
     *
     * @param o Object to store, of type BlockManager
     * @return Element containing the complete info
     */
    public Element store(Object o) {
        Element blocks = new Element("blocks");
        setStoreElementClass(blocks);
        BlockManager tm = (BlockManager) o;
        if (tm!=null) {
            java.util.Iterator<String> iter =
                                    tm.getSystemNameList().iterator();

            // don't return an element if there are not blocks to include
            if (!iter.hasNext()) return null;
            blocks.addContent(new Element("defaultspeed").addContent(tm.getDefaultSpeed()));
            // write out first set of blocks without contents
             while (iter.hasNext()) {
                String sname = iter.next();
                if (sname==null) log.error("System name null during store");
                //Block b = tm.getBySystemName(sname);
                Element elem = new Element("block")
                            .setAttribute("systemName", sname);
                elem.addContent(new Element("systemName").addContent(sname));
                if (log.isDebugEnabled()) log.debug("initial store Block "+sname);
                
                // and put this element out
                blocks.addContent(elem);
            }
           
            // write out again with contents
            iter = tm.getSystemNameList().iterator();
            while (iter.hasNext()) {
                String sname = iter.next();
                if (sname==null) log.error("System name null during store");
                Block b = tm.getBySystemName(sname);
                String uname = b.getUserName();
                Element elem = new Element("block")
                            .setAttribute("systemName", sname);
                elem.addContent(new Element("systemName").addContent(sname));
                if (log.isDebugEnabled()) log.debug("second store Block "+sname+":"+uname);
				// store length and curvature attributes
				elem.setAttribute("length", Float.toString(b.getLengthMm()));
				elem.setAttribute("curve", Integer.toString(b.getCurvature()));
                if((b.getBlockSpeed()!=null) && (!b.getBlockSpeed().equals("")) && !b.getBlockSpeed().contains("Global")){
                    elem.addContent(new Element("speed").addContent(b.getBlockSpeed()));
                }
                // store common parts
                storeCommon(b, elem);
                String perm = "no";
                if (b.getPermissiveWorking())
                    perm = "yes";
                elem.addContent(new Element("permissive").addContent(perm));
                // Add content. First, the sensor.
                Sensor s = b.getSensor();
                if (s!=null) {
                    Element se = new Element("sensor");
                    se.setAttribute("systemName", s.getSystemName());
                    elem.addContent(se);
                }
                
                if(tm.savePathInfo()){
                    // then the paths
                    List<Path> paths = b.getPaths();
                    for (int i=0; i<paths.size(); i++)
                        addPath(elem, paths.get(i));
                    // and put this element out
                }
                blocks.addContent(elem);
            }
        }
        return blocks;
    }

    void addPath(Element e, Path p) {
        // for now, persist two directions and a bean setting
        Element pe = new Element("path");
        pe.setAttribute("todir", ""+p.getToBlockDirection());
        pe.setAttribute("fromdir", ""+p.getFromBlockDirection());
        if (p.getBlock()!=null)
            pe.setAttribute("block", ""+p.getBlock().getSystemName());
        List<BeanSetting> l = p.getSettings();
        if (l!=null) {
            for (int i=0; i<l.size(); i++) {
                addBeanSetting(pe, l.get(i));
            }
        }
        e.addContent(pe);
    }
    void addBeanSetting(Element e, BeanSetting bs) {
		if (bs.getBean() == null) {
			log.error("Invalid BeanSetting - did not save");
			return;
		}
        // persist bean name, type and value
        Element bse = new Element("beansetting");
        // for now, assume turnout
        bse.setAttribute("setting", ""+bs.getSetting());
        Element be = new Element("turnout");
        be.setAttribute("systemName", bs.getBean().getSystemName());
        bse.addContent(be);
        e.addContent(bse);
    }
    
    /**
     * Load Blocks into the existing BlockManager.
     * <p>
     * The BlockManager in the InstanceManager is created automatically.
     * 
     * @param blocks Element containing the block elements to load.
     * @return true if successful
     */
    @SuppressWarnings("unchecked")
	public boolean load(Element blocks) throws jmri.configurexml.JmriConfigureXmlException {
    	boolean result = true;
        try {
            if (blocks.getChild("defaultspeed")!=null){
                String speed = blocks.getChild("defaultspeed").getText();
                if (speed!=null && !speed.equals("")){
                    InstanceManager.blockManagerInstance().setDefaultSpeed(speed);
                }
            }
        } catch (jmri.JmriException ex) {
            log.error(ex.toString());
        }
        
        List<Element> list = blocks.getChildren("block");
        if (log.isDebugEnabled()) log.debug("Found "+list.size()+" objects");
        //BlockManager tm = InstanceManager.blockManagerInstance();

        for (int i=0; i<list.size(); i++) {
            Element block = list.get(i);
            loadBlock(block);
        }
        return result;
    }

    /**
     * Utility method to load the individual Block objects.
     * 
     * @param element Element holding one block
     */
    @SuppressWarnings("unchecked")
	public void loadBlock(Element element) throws jmri.configurexml.JmriConfigureXmlException {
            if (element.getAttribute("systemName") == null) {
                log.warn("unexpected null in systemName "+element+" "+element.getAttributes());
                return;
            }
            String sysName = getSystemName(element);
            String userName = getUserName(element);
            if (log.isDebugEnabled()) log.debug("defined Block: ("+sysName+")("+(userName==null?"<null>":userName)+")");

            Block block = InstanceManager.blockManagerInstance().getBlock(sysName);
            if (block==null) { // create it if doesn't exist
                InstanceManager.blockManagerInstance().createNewBlock(sysName, userName);
                block = InstanceManager.blockManagerInstance().getBlock(sysName);
            }
            if (userName!=null) block.setUserName(userName);
			if (element.getAttribute("length") != null) {
				// load length in millimeters
				block.setLength(Float.valueOf(element.getAttribute("length").getValue()).floatValue());
			}
			if (element.getAttribute("curve") != null) {
				// load curve attribute
				block.setCurvature(Integer.parseInt((element.getAttribute("curve")).getValue()));
			}
            try {
                block.setBlockSpeed("Global");
                if (element.getChild("speed")!=null){
                    String speed = element.getChild("speed").getText();
                    if (speed!=null && !speed.equals("") && !speed.contains("Global")){
                        block.setBlockSpeed(speed);
                    }
                }
            } catch (jmri.JmriException ex) {
                log.error(ex.toString());
            }
            if(element.getChild("permissive")!=null){
                boolean permissive = false;
                if (element.getChild("permissive").getText().equals("yes"))
                    permissive = true;
                block.setPermissiveWorking(permissive);
            }
            // load common parts
            loadCommon(block, element);
            
            // load sensor if present
            List<Element> sensors = element.getChildren("sensor");
            if (sensors.size()>1) log.error("More than one sensor present: "+sensors.size());
            if (sensors.size()==1) {
                // sensor
                String name = sensors.get(0).getAttribute("systemName").getValue();
                Sensor sensor = InstanceManager.sensorManagerInstance().provideSensor(name);
                block.setSensor(sensor);
            }
            
            // load paths if present
            List<Element> paths = element.getChildren("path");
            if (paths.size()>0 && block.getPaths().size()>0) 
                log.warn("Adding "+paths.size()+" paths to block "+sysName+" that already has "+block.getPaths().size()+" blocks. Please report this as an error.");
            for (int i=0; i<paths.size(); i++) {
                Element path = paths.get(i);
                loadPath(block, path);
            }
    }

    /**
     * Load path into an existing Block.
     *
     * @param block Block to receive path
     * @param element Element containing path information
     */
    @SuppressWarnings("unchecked")
	public void loadPath(Block block, Element element) throws jmri.configurexml.JmriConfigureXmlException {
        // load individual path
        int toDir = 0;
        int fromDir = 0;
        try {
            toDir = element.getAttribute("todir").getIntValue();
            fromDir = element.getAttribute("fromdir").getIntValue();
        } catch (org.jdom.DataConversionException e) {
            log.error("Could not parse path attribute");
        } catch (NullPointerException e) {
            creationErrorEncountered (org.apache.log4j.Level.ERROR,
                                        "Block Path entry in file missing required attribute",
                                        block.getSystemName(),block.getUserName(),null);
        }
        
        Block toBlock = null;
        if (element.getAttribute("block")!=null) {
            String name = element.getAttribute("block").getValue();
            toBlock = InstanceManager.blockManagerInstance().getBlock(name);
        }
        Path path = new Path(toBlock, toDir, fromDir);
        
        List<Element> settings = element.getChildren("beansetting");
        for (int i=0; i<settings.size(); i++) {
            Element setting = settings.get(i);
            loadBeanSetting(path, setting);
        }
        
        block.addPath(path);
    }

    /**
     * Load BeanSetting into an existing Path.
     * 
     * @param path Path to receive BeanSetting
     * @param element Element containing beansetting information
     */
    @SuppressWarnings("unchecked")
	public void loadBeanSetting(Path path, Element element) {
        int setting = 0;
        try {
            setting = element.getAttribute("setting").getIntValue();
        } catch (org.jdom.DataConversionException e) {
            log.error("Could not parse beansetting attribute");
        }
        List<Element> turnouts = element.getChildren("turnout");
        if (turnouts.size()!=1) log.error("invalid number of turnout element children");
        String name = turnouts.get(0).getAttribute("systemName").getValue();
        Turnout t = InstanceManager.turnoutManagerInstance().provideTurnout(name);
        
        BeanSetting bs = new BeanSetting(t, setting);
        path.addSetting(bs);

    }

    static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(BlockManagerXml.class.getName());
}
