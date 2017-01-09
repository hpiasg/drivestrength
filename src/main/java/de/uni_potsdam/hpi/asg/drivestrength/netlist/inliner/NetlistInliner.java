package de.uni_potsdam.hpi.asg.drivestrength.netlist.inliner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni_potsdam.hpi.asg.drivestrength.netlist.GateInstance;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.Module;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.ModuleInstance;
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
    	inlinedNetlist.addModule(createInlinedModuleRecursively(originalNetlist.getRootModule()));
    	return new Netlist();
    }
    
    private void assertNetlistIsFlat() {
    	if (!this.originalNetlist.isFlat()) {
        	logger.warn("NetlistInliner called on non-flat netlist (meaning it has submodule definition instanciated more than once)");    		
    	}
    }
    
    private Module createInlinedModuleRecursively(Module currentModule) {
    	Module inlinedModule = new Module(currentModule);
        int nextInstanceId = 0;
        int nextWireId = 0;
    	for (ModuleInstance instance : currentModule.getModuleInstances()) {
    		System.out.println("inlining " + instance.getDefinition().getName() + " into " + inlinedModule.getName());
    		Module inlinedChild = createInlinedModuleRecursively(instance.getDefinition());
    		//TODO: rename i/o signals to parent signals (or rather, assign corresponding parent signals to gate instance pins)
    		//TODO: rename wire signals with unique
    		//TODO: copy assign statements
    		for (GateInstance childGateInstance : inlinedChild.getGateInstances()) {
    			System.out.println("todo: adding gate instance " + childGateInstance.getName() + " to " + inlinedModule.getName());
    		}
    	}
    	return inlinedModule;
    }
}
