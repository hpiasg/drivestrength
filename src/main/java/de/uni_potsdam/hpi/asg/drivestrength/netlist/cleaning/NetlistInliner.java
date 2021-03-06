package de.uni_potsdam.hpi.asg.drivestrength.netlist.cleaning;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni_potsdam.hpi.asg.drivestrength.netlist.Netlist;

public class NetlistInliner {

    protected static final Logger logger = LogManager.getLogger();
    private Netlist originalNetlist;

    public NetlistInliner(Netlist originalNetlist) {
        this.originalNetlist = originalNetlist;
    }

    public Netlist run() {
        assertNetlistIsFlat();
        Netlist inlinedNetlist = new Netlist();
        inlinedNetlist.setName(originalNetlist.getName());
        inlinedNetlist.addModule(new NetlistModuleInliner(originalNetlist.getRootModule()).run());
        return inlinedNetlist;
    }

    private void assertNetlistIsFlat() {
        if (!this.originalNetlist.isFlat()) {
            logger.warn("NetlistInliner called on non-flat netlist (meaning it has submodule definition instanciated more than once)");
        }
    }
}
