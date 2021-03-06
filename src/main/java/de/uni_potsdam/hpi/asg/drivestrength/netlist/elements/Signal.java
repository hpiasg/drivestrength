package de.uni_potsdam.hpi.asg.drivestrength.netlist.elements;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Signal {
    protected static final Logger logger = LogManager.getLogger();

    public enum Direction {
        input, output, wire, supply0, supply1, constant
    }

    protected String    name;
    protected Direction direction;
    protected int       width;
    protected int       bitOffset;


    private static Signal zeroInstance;
    private static Signal oneInstance;

    public static Signal getZeroInstance() {
        if (zeroInstance == null) {
            zeroInstance = new Signal("1'b0", Direction.constant, 1, 0);
        }
        return zeroInstance;
    }

    public static Signal getOneInstance() {
        if (oneInstance == null) {
            oneInstance = new Signal("1'b1", Direction.constant, 1, 0);
        }
        return oneInstance;
    }

    public static boolean isConstantName(String signalName) {
        return signalName.equals("0") || signalName.equals("1") || signalName.equals("1'b0") || signalName.equals("1'b1");
    }

    public Signal(String name, Direction direction, int width, int bitOffset) {
        this.direction = direction;
        this.name = name;
        this.width = width;
        this.bitOffset = bitOffset;
    }

    public Signal(Signal signalToCopy) {
        this.direction = signalToCopy.getDirection();
        this.name = signalToCopy.getName();
        this.width = signalToCopy.getWidth();
        this.bitOffset = signalToCopy.getBitOffset();
    }

    public String getName() {
        return name;
    }

    public void setName(String newName) {
    	this.name = newName;
    }

    public int getWidth() {
        return width;
    }

    public int getBitOffset() {
        return bitOffset;
    }

    public boolean isBundle() {
        return width > 1;
    }

    @Override
    public String toString() {
        return name + ":" + direction + ",width:" + width;
    }

    public String toVerilog() {
        String directionString = this.direction.toString();
        String bundleString = "";
        if (width > 1) {
            bundleString = " [" + Integer.toString(width - 1 + bitOffset) + ":" + Integer.toString(bitOffset) + "]";
        }
        return directionString + bundleString + " " + this.name + ";";
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean isIOSignal() {
    	return (direction == Direction.input || direction == Direction.output);
    }

    public boolean isWire() {
        return (direction == Direction.wire);
    }

    public boolean isConstant() {
        return (direction == Direction.constant);
    }
}
